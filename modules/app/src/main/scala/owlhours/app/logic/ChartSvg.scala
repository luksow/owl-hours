package owlhours.app.logic

import java.time.Instant
import owlhours.domain.{BacUnit, Format, Intake}
import owlhours.model.Curve

enum MarkerKind:
  case Beer, Wine, Shot, Water, Meal

final case class Marker(at: Instant, kind: MarkerKind)

object Marker:
  def of(intakes: Seq[Intake]): List[Marker] = intakes.toList.map {
    case d: Intake.Drink =>
      val kind =
        if d.snapshot.volume.unwrap >= 250 then MarkerKind.Beer
        else if d.snapshot.abv.unwrap >= 25 then MarkerKind.Shot
        else MarkerKind.Wine
      Marker(d.start, kind)
    case w: Intake.Water => Marker(w.start, MarkerKind.Water)
    case m: Intake.Meal  => Marker(m.at, MarkerKind.Meal)
  }

/** The light-trail BAC chart (spec §8.3) as SVG markup; mounted with InnerHtml. */
object ChartSvg:
  private val (x0, x1, yTop, yBottom) = (12.0, 346.0, 14.0, 170.0)
  private val Ice   = "#5CE1FF"
  private val Amber = "#FFB03B"
  private val Pink  = "#FF3F8E"
  private val Ink   = "#FFF3E4"
  private val Dim   = "#C7A6AE"
  private val MgDl12 = 1.2 * BacUnit.MgDlPerPermille

  // the app's neon line icons at half size; declared after the colours (fields initialise in order)
  private val Glyph: Map[MarkerKind, (String, String)] = Map(
    MarkerKind.Beer  -> (Pink, Icons.path(Icon.Beer)),
    MarkerKind.Wine  -> (Pink, Icons.path(Icon.Wine)),
    MarkerKind.Shot  -> (Pink, Icons.path(Icon.Shot)),
    MarkerKind.Water -> (Ice, Icons.path(Icon.Water)),
    MarkerKind.Meal  -> (Amber, Icons.path(Icon.Food))
  )
  private val MarkerGap = 14.0 // px between pictograms in one row

  /** Drinks on the top row, water and food on the second (spec §3); within a row, a marker too close to the previous
    * one is nudged right so pictograms never overlap. */
  private[logic] def rows(markers: List[(Double, MarkerKind)]): List[(Double, Int)] =
    val row = (k: MarkerKind) => k match
      case MarkerKind.Water | MarkerKind.Meal => 1
      case _                                  => 0
    markers.foldLeft((Vector.empty[(Double, Int)], Map.empty[Int, Double])) { case ((acc, last), (x, k)) =>
      val r  = row(k)
      val x2 = last.get(r).fold(x)(prev => math.max(x, prev + MarkerGap))
      (acc :+ (x2 -> r), last.updated(r, x2))
    }._1.toList

  /** Every 0.5 ‰, or every 1 ‰ when the scale tops 3 ‰; never more than about seven labels, none singled out. */
  def gridSteps(topPermille: Double): List[Double] =
    val step = if topPermille > 3 then 1.0 else 0.5
    Iterator.iterate(0.0)(_ + step).takeWhile(_ <= topPermille + 1e-9).map(v => math.round(v * 10) / 10.0).toList

  /** `utcOffsetMinutes` gives the local offset at an instant, so hour ticks land on local wall-clock hours. */
  def render(t: Tonight, now: Instant, markers: List[Marker], utcOffsetMinutes: Instant => Int, timeLabel: Instant => String,
      ghost: Option[Curve] = None, bacLabel: Double => String = p => s"${Format.number(p)}‰",
      label: String = "Blood alcohol estimate over tonight", showNow: Boolean = true): String =
    val from    = t.viewFrom.toEpochMilli.toDouble
    val to      = t.viewTo.toEpochMilli.toDouble
    val samples = t.projection.curve.samples
    val inView  = samples.indices.filter(i => samples(i).at.toEpochMilli >= from && samples(i).at.toEpochMilli <= to)
    val highest = (inView.map(t.projection.high) :+ 0.0).max
    val yMax    = math.max(BacUnit.MgDlPerPermille, highest * 1.15)

    def x(at: Instant) = x0 + (x1 - x0) * (at.toEpochMilli - from) / (to - from)
    def y(mgdl: Double) = yBottom - (yBottom - yTop) * math.min(mgdl, yMax) / yMax
    def n(v: Double)    = f"$v%.1f"
    def path(points: Seq[(Double, Double)]) =
      points.zipWithIndex.map { case ((px, py), i) => s"${if i == 0 then "M" else "L"}${n(px)} ${n(py)}" }.mkString(" ")

    val nowMs   = now.toEpochMilli
    val past    = inView.filter(i => samples(i).at.toEpochMilli <= nowMs).map(i => (x(samples(i).at), y(samples(i).bacMgDl)))
    val future  = inView.filter(i => samples(i).at.toEpochMilli >= nowMs).map(i => (x(samples(i).at), y(samples(i).bacMgDl)))
    val bandTop = inView.map(i => (x(samples(i).at), y(t.projection.high(i))))
    val bandLow = inView.reverse.map(i => (x(samples(i).at), y(t.projection.low(i))))

    val sb = new StringBuilder
    sb ++= s"""<svg viewBox="0 0 358 232" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="${Markup.escape(label)}">"""
    sb ++= s"""<defs><linearGradient id="oh-trail" gradientUnits="userSpaceOnUse" x1="0" x2="0" y1="${n(y(0))}" y2="${n(y(MgDl12))}">"""
    sb ++= s"""<stop offset="0" stop-color="$Ice"/><stop offset="0.6667" stop-color="$Amber"/><stop offset="1" stop-color="$Pink"/></linearGradient>"""
    sb ++= """<filter id="oh-glow" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="2.2" result="b"/>"""
    sb ++= """<feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>"""
    sb ++= s"""<line x1="${n(x0)}" x2="${n(x1)}" y1="${n(yBottom)}" y2="${n(yBottom)}" stroke="$Dim" stroke-opacity="0.3"/>"""
    // a scale cue every half per mille, all alike, so no line reads as a target (spec §3)
    for k <- gridSteps(yMax / BacUnit.MgDlPerPermille) do
      val gy = y(k * BacUnit.MgDlPerPermille)
      sb ++= """<g class="oh-grid">"""
      if k > 0 then
        sb ++= s"""<line x1="${n(x0)}" x2="${n(x1)}" y1="${n(gy)}" y2="${n(gy)}" stroke="$Dim" stroke-opacity="0.18" stroke-dasharray="1 5"/>"""
      // a label that would sit under the peak label's top band goes below its line instead
      val ly = if k == 0 then gy - 3 else if gy - 3 < 16 then gy + 10 else gy - 3
      sb ++= s"""<text x="${n(x1)}" y="${n(ly)}" text-anchor="end" font-size="9" fill="$Dim">${Markup.escape(bacLabel(k))}</text></g>"""
    if bandTop.nonEmpty then
      sb ++= s"""<path class="oh-band" d="${path(bandTop ++ bandLow)} Z" fill="url(#oh-trail)" fill-opacity="0.16"/>"""
    for g <- ghost do
      val pts = g.samples.filter(s => s.at.toEpochMilli >= from && s.at.toEpochMilli <= to).map(s => (x(s.at), y(s.bacMgDl)))
      if pts.size > 1 then
        sb ++= s"""<path class="oh-ghost" d="${path(pts)}" fill="none" stroke="$Dim" stroke-width="1.5" stroke-dasharray="2 4" stroke-opacity="0.7"/>"""
    if future.size > 1 then
      sb ++= s"""<path class="oh-projection" stroke-dasharray="5 6" d="${path(future)}" fill="none" stroke="url(#oh-trail)" stroke-width="2.5" stroke-linecap="round" opacity="0.85"/>"""
    if past.size > 1 then
      sb ++= s"""<path class="oh-past" d="${path(past)}" fill="none" stroke="url(#oh-trail)" stroke-width="3" stroke-linecap="round" filter="url(#oh-glow)"/>"""
    val nowIn = showNow && nowMs >= from && nowMs <= to
    if nowIn then
      val nx = x(now)
      sb ++= s"""<g class="oh-now"><line x1="${n(nx)}" x2="${n(nx)}" y1="${n(yTop)}" y2="${n(yBottom)}" stroke="$Ink" stroke-width="1.2" stroke-opacity="0.8"/></g>"""
    // the peak, labelled, and the moment the estimate is back to ~0, as glowing dots (spec §3)
    for pk <- t.summary.peak if pk.at.toEpochMilli >= from && pk.at.toEpochMilli <= to do
      val (px, py) = (x(pk.at), y(pk.bacMgDl))
      val text     = s"${bacLabel(pk.bacMgDl / BacUnit.MgDlPerPermille)} · ${timeLabel(pk.at)}"
      val lx       = math.max(x0 + 30, math.min(x1 - 30, px))
      sb ++= s"""<g class="oh-peak"><circle cx="${n(px)}" cy="${n(py)}" r="3.5" fill="$Amber" filter="url(#oh-glow)"/>"""
      sb ++= s"""<text x="${n(lx)}" y="${n(math.max(10, py - 8))}" text-anchor="middle" font-size="10" fill="$Ink">${Markup.escape(text)}</text></g>"""
    for b <- t.summary.backToZeroBy if b.toEpochMilli >= from && b.toEpochMilli <= to do
      sb ++= s"""<g class="oh-back"><circle cx="${n(x(b))}" cy="${n(yBottom)}" r="3.5" fill="$Ice" filter="url(#oh-glow)"/></g>"""
    if nowIn then
      val nx  = x(now)
      val cur = samples.findLast(!_.at.isAfter(now)).getOrElse(samples.head)
      sb ++= s"""<g class="oh-now-dot"><circle cx="${n(nx)}" cy="${n(y(cur.bacMgDl))}" r="4.5" fill="#120A0E" stroke="$Ice" stroke-width="2"/>"""
      sb ++= s"""<circle cx="${n(nx)}" cy="${n(y(cur.bacMgDl))}" r="2" fill="$Ice"/></g>"""
    val visible = markers.filter(m => m.at.toEpochMilli >= from && m.at.toEpochMilli <= to).sortBy(_.at.toEpochMilli)
    for (m, (mx, row)) <- visible.zip(rows(visible.map(m => (x(m.at), m.kind)))) do
      val (colour, d) = Glyph(m.kind)
      sb ++= s"""<g class="oh-marker" transform="translate(${n(mx - 6)} ${n(176 + row * 15.0)})">"""
      sb ++= s"""<path d="$d" transform="scale(0.5)" fill="none" stroke="$colour" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></g>"""
    val stepMs = if to - from < 6 * 3600 * 1000 then 3600 * 1000L else 2 * 3600 * 1000L
    // the offset is read at every tick, so ticks stay on local hours across a DST change
    def align(t: Long): Long =
      val off = utcOffsetMinutes(Instant.ofEpochMilli(t)) * 60000L
      (((t + off + stepMs - 1) / stepMs) * stepMs) - off
    val pillX = Option.when(nowIn)(x(now))
    var tick = align(from.toLong)
    while tick <= to.toLong do
      val at = Instant.ofEpochMilli(tick)
      // an hour label under the now pill would be hidden by it
      if !pillX.exists(px => math.abs(x(at) - px) < 24) then
        sb ++= s"""<text x="${n(x(at))}" y="226" text-anchor="middle" font-size="11" fill="$Dim">${Markup.escape(timeLabel(at))}</text>"""
      tick = align(tick + stepMs)
    // the now pill sits on the time axis (identity prototype, section 06)
    for nx <- pillX do
      sb ++= s"""<g class="oh-now-pill"><rect x="${n(nx - 16)}" y="214" width="32" height="13" rx="6.5" fill="$Ice"/>"""
      sb ++= s"""<text x="${n(nx)}" y="223.5" text-anchor="middle" font-size="9" font-weight="700" fill="#120A0E">now</text></g>"""
    sb ++= "</svg>"
    sb.toString
