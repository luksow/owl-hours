package owlhours.model

import java.time.Instant

enum Trend:
  case Rising, Falling, Steady

final case class Peak(at: Instant, bacMgDl: Double)

final case class Summary(currentMgDl: Double, trend: Trend, peak: Option[Peak], backToZeroBy: Option[Instant])

object Summary:
  /** Change over the next 10 minutes, in mg/dL per hour, beyond which the trend is not steady. */
  private val TrendThresholdPerHour = 1.0
  private val MinPeakMgDl            = 1.0

  def of(curve: Curve, now: Instant): Summary =
    val s = curve.samples
    require(s.nonEmpty, "curve must not be empty")
    val i       = indexAt(s, now)
    val current = s(i).bacMgDl
    val ahead   = indexAt(s, now.plusSeconds(600))
    val minutes = Simulation.minutesBetween(s(i).at, s(ahead).at)
    val slope   = if minutes > 0 then (s(ahead).bacMgDl - current) / minutes * 60 else 0.0
    val trend =
      if slope > TrendThresholdPerHour then Trend.Rising
      else if slope < -TrendThresholdPerHour then Trend.Falling
      else Trend.Steady
    val max  = s.maxBy(_.bacMgDl)
    val peak = Option.when(max.bacMgDl >= MinPeakMgDl)(Peak(max.at, max.bacMgDl))
    val lastAbove = s.lastIndexWhere(_.bacMgDl >= Simulation.ZeroThresholdMgDl)
    val backToZero =
      if lastAbove < i || lastAbove == s.size - 1 then None
      else Some(s(lastAbove + 1).at)
    Summary(current, trend, peak, backToZero)

  /** Index of the last sample at or before `t`, clamped to the curve. */
  private def indexAt(s: Vector[BacSample], t: Instant): Int =
    val j = s.lastIndexWhere(!_.at.isAfter(t))
    if j < 0 then 0 else j
