package owlhours.app.logic

import java.time.Instant
import owlhours.domain.*
import owlhours.model.*

/** The night with a candidate intake versus without it; nothing is saved. */
final case class WhatIf(
    tonight: Tonight,
    ghost: Option[Curve],
    peakBefore: Option[Peak],
    peakAfter: Option[Peak],
    zeroBefore: Option[Instant],
    zeroAfter: Option[Instant],
    markers: List[Marker]
)

object WhatIf:
  def of(p: Profile, sessions: Vector[Session], candidate: Intake, now: Instant): WhatIf =
    val night     = Sessions.add(p, sessions, candidate).find(_.intakes.contains(candidate)).get // add always files it
    val without   = night.intakes.diff(Vector(candidate))
    val projected = Tonight.project(p, Some(night), now)
    val after     = Tonight.at(projected, now)
    val ghost = Option.when(without.nonEmpty)(
      Simulation.simulate(p, without, projected.from, projected.to, Tonight.StepMinutes))
    val before = ghost.map(Summary.of(_, now))
    WhatIf(after, ghost, before.flatMap(_.peak), after.summary.peak, before.flatMap(_.backToZeroBy),
      after.summary.backToZeroBy, Marker.of(night.intakes))

  def describe(w: WhatIf, unit: BacUnit, time: Instant => String): List[String] =
    val peak = (w.peakBefore, w.peakAfter) match
      case (Some(b), Some(a)) => Some(s"Peak ${Display.peak(b.bacMgDl, unit)} → ${Display.peak(a.bacMgDl, unit)} at ${time(a.at)}")
      case (None, Some(a))    => Some(s"Peak ${Display.peak(a.bacMgDl, unit)} at ${time(a.at)}")
      case _                  => None
    val zero = (w.zeroBefore, w.zeroAfter) match
      case (Some(b), Some(a)) => Some(s"Back to ~0 by ${time(b)} → ${time(a)}")
      case (None, Some(a))    => Some(s"Back to ~0 by ${time(a)}")
      case _                  => None
    peak.toList ++ zero.toList
