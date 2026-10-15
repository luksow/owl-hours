package owlhours.app.logic

import java.time.Instant
import owlhours.domain.BacUnit

enum MarkerKind:
  case Drink, Water, Meal

final case class Marker(at: Instant, kind: MarkerKind)

/** The light-trail BAC chart (spec §8.3) as SVG markup; mounted with InnerHtml. */
object ChartSvg:
  private val (x0, x1, yTop, yBottom) = (12.0, 346.0, 14.0, 170.0)
  private val Ice   = "#5CE1FF"
  private val Amber = "#FFB03B"
  private val Pink  = "#FF3F8E"
  private val Ink   = "#FFF3E4"
  private val Dim   = "#C7A6AE"
  private val MgDl12 = 1.2 * BacUnit.MgDlPerPermille

  /** `utcOffsetMinutes` gives the local offset at an instant, so hour ticks land on local wall-clock hours. */
  def render(t: Tonight, now: Instant, markers: List[Marker], utcOffsetMinutes: Instant => Int, timeLabel: Instant => String): String =
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
    sb ++= s"""<svg viewBox="0 0 358 220" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Blood alcohol estimate over tonight">"""
    sb ++= s"""<defs><linearGradient id="oh-trail" gradientUnits="userSpaceOnUse" x1="0" x2="0" y1="${n(y(0))}" y2="${n(y(MgDl12))}">"""
    sb ++= s"""<stop offset="0" stop-color="$Ice"/><stop offset="0.6667" stop-color="$Amber"/><stop offset="1" stop-color="$Pink"/></linearGradient>"""
    sb ++= """<filter id="oh-glow" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="2.2" result="b"/>"""
    sb ++= """<feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>"""
    sb ++= s"""<line x1="${n(x0)}" x2="${n(x1)}" y1="${n(yBottom)}" y2="${n(yBottom)}" stroke="$Dim" stroke-opacity="0.3"/>"""
    if bandTop.nonEmpty then
      sb ++= s"""<path class="oh-band" d="${path(bandTop ++ bandLow)} Z" fill="url(#oh-trail)" fill-opacity="0.16"/>"""
    if future.size > 1 then
      sb ++= s"""<path class="oh-projection" stroke-dasharray="5 6" d="${path(future)}" fill="none" stroke="url(#oh-trail)" stroke-width="2.5" stroke-linecap="round" opacity="0.85"/>"""
    if past.size > 1 then
      sb ++= s"""<path class="oh-past" d="${path(past)}" fill="none" stroke="url(#oh-trail)" stroke-width="3" stroke-linecap="round" filter="url(#oh-glow)"/>"""
    if nowMs >= from && nowMs <= to then
      val nx = x(now)
      sb ++= s"""<g class="oh-now"><line x1="${n(nx)}" x2="${n(nx)}" y1="${n(yTop)}" y2="${n(yBottom)}" stroke="$Ink" stroke-width="1.2" stroke-opacity="0.8"/>"""
      sb ++= s"""<rect x="${n(nx - 16)}" y="0" width="32" height="13" rx="6.5" fill="$Ink"/>"""
      sb ++= s"""<text x="${n(nx)}" y="9.5" text-anchor="middle" font-size="9" font-weight="700" fill="#120A0E">now</text></g>"""
    for m <- markers if m.at.toEpochMilli >= from && m.at.toEpochMilli <= to do
      val colour = m.kind match
        case MarkerKind.Drink => Pink
        case MarkerKind.Water => Ice
        case MarkerKind.Meal  => Amber
      sb ++= s"""<circle class="oh-marker" cx="${n(x(m.at))}" cy="182" r="4" fill="$colour"/>"""
    val stepMs   = if to - from < 6 * 3600 * 1000 then 3600 * 1000L else 2 * 3600 * 1000L
    val offsetMs = utcOffsetMinutes(t.viewFrom) * 60000L
    var tick     = (((from.toLong + offsetMs + stepMs - 1) / stepMs) * stepMs) - offsetMs
    while tick <= to.toLong do
      val at = Instant.ofEpochMilli(tick)
      sb ++= s"""<text x="${n(x(at))}" y="206" text-anchor="middle" font-size="11" fill="$Dim">${Markup.escape(timeLabel(at))}</text>"""
      tick += stepMs
    sb ++= "</svg>"
    sb.toString
