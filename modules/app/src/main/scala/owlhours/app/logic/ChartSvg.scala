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

  // 24×24 line pictograms from the identity prototype; declared after the colours (fields initialise in order).
  private val Glyph: Map[MarkerKind, (String, String)] = Map(
    MarkerKind.Beer  -> (Pink, "M5 8h10v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2z M15 10h2.5a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H15 M5 8c0-3 2.5-4 5-3 2-2 5-1 5 3 M8.5 12v6M11.5 12v6"),
    MarkerKind.Wine  -> (Pink, "M7 3h10c0 6-2 9.5-5 9.5S7 9 7 3z M12 12.5V20M8 20h8M7.6 7h8.8"),
    MarkerKind.Shot  -> (Pink, "M7 4h10l-1.6 16H8.6z M8 11h8"),
    MarkerKind.Water -> (Ice, "M12 3c-4 5.5-6.5 8.8-6.5 12a6.5 6.5 0 0 0 13 0c0-3.2-2.5-6.5-6.5-12z M9 15.5a3 3 0 0 0 2 3"),
    MarkerKind.Meal  -> (Amber, "M4 10c0-6 16-6 16 0z M3.5 13.5h17 M5 17h14v.5a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 17.5z")
  )
  private val MarkerGap = 14.0 // px between pictograms in one row

  /** Each marker goes to the first of two rows where it does not touch the previous one; the second row may overlap. */
  private[logic] def rows(xs: List[Double]): List[Int] =
    xs.foldLeft((List.empty[Int], Vector(Double.NegativeInfinity, Double.NegativeInfinity))) { case ((acc, last), x) =>
      val row = if x - last(0) >= MarkerGap then 0 else 1
      (acc :+ row, last.updated(row, x))
    }._1

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
    // a scale cue at each whole per mille; never at 0.5, which would read as a target
    for k <- 1 to (yMax / BacUnit.MgDlPerPermille).toInt do
      val gy = y(k * BacUnit.MgDlPerPermille)
      sb ++= s"""<g class="oh-grid"><line x1="${n(x0)}" x2="${n(x1)}" y1="${n(gy)}" y2="${n(gy)}" stroke="$Dim" stroke-opacity="0.18" stroke-dasharray="1 5"/>"""
      // the now pill occupies the top 13 px, so a label that would sit there goes below its line instead
      val ly = if gy - 3 < 16 then gy + 10 else gy - 3
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
    if showNow && nowMs >= from && nowMs <= to then
      val nx = x(now)
      sb ++= s"""<g class="oh-now"><line x1="${n(nx)}" x2="${n(nx)}" y1="${n(yTop)}" y2="${n(yBottom)}" stroke="$Ink" stroke-width="1.2" stroke-opacity="0.8"/>"""
      sb ++= s"""<rect x="${n(nx - 16)}" y="0" width="32" height="13" rx="6.5" fill="$Ink"/>"""
      sb ++= s"""<text x="${n(nx)}" y="9.5" text-anchor="middle" font-size="9" font-weight="700" fill="#120A0E">now</text></g>"""
    val visible = markers.filter(m => m.at.toEpochMilli >= from && m.at.toEpochMilli <= to).sortBy(_.at.toEpochMilli)
    for (m, row) <- visible.zip(rows(visible.map(m => x(m.at)))) do
      val (colour, d) = Glyph(m.kind)
      sb ++= s"""<g class="oh-marker" transform="translate(${n(x(m.at) - 6)} ${n(176 + row * 15.0)})">"""
      sb ++= s"""<path d="$d" transform="scale(0.5)" fill="none" stroke="$colour" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></g>"""
    val stepMs = if to - from < 6 * 3600 * 1000 then 3600 * 1000L else 2 * 3600 * 1000L
    // the offset is read at every tick, so ticks stay on local hours across a DST change
    def align(t: Long): Long =
      val off = utcOffsetMinutes(Instant.ofEpochMilli(t)) * 60000L
      (((t + off + stepMs - 1) / stepMs) * stepMs) - off
    var tick = align(from.toLong)
    while tick <= to.toLong do
      val at = Instant.ofEpochMilli(tick)
      sb ++= s"""<text x="${n(x(at))}" y="226" text-anchor="middle" font-size="11" fill="$Dim">${Markup.escape(timeLabel(at))}</text>"""
      tick = align(tick + stepMs)
    sb ++= "</svg>"
    sb.toString
