package owlhours.app.logic

import java.time.{Duration, Instant}
import owlhours.domain.*
import owlhours.model.*

final case class NightSummary(
    session: Session,
    peak: Option[Peak],
    drinks: Int,
    standardDrinks: Double,
    duration: Duration,
    badges: List[Badge]
)

/** Past nights, re-estimated with the current profile. One simulation per night. */
object History {
  def past(p: Profile, ss: Vector[Session], now: Instant): Vector[Session] = {
    val open = Sessions.active(p, ss, now)
    ss.filterNot(s => open.contains(s)).reverse
  }

  def summary(p: Profile, s: Session, standard: StandardDrink): NightSummary = {
    val drinks  = s.intakes.collect { case d: Intake.Drink => d }
    val lastEnd = s.intakes.map(Sessions.endOf).maxBy(_.toEpochMilli)
    val curve   = Simulation.simulate(p, s.intakes, s.startedAt, lastEnd.plusSeconds(24 * 3600), stepMinutes = 5)
    def bacAt(t: Instant) = curve.samples.findLast(!_.at.isAfter(t)).fold(0.0)(_.bacMgDl)
    NightSummary(
      s,
      Summary.of(curve, s.startedAt).peak,
      drinks.size,
      drinks.map(_.snapshot.ethanolGrams).sum / standard.grams,
      Duration.between(s.startedAt, s.endedAt.getOrElse(lastEnd)),
      Badges.of(s, bacAt)
    )
  }

  /** The night's chart as Tonight draws it, read at back-to-~0 (or the last intake when it never rose). */
  def chart(p: Profile, s: Session): (Tonight, Instant) = {
    val projected = Tonight.project(p, Some(s), s.startedAt)
    val lastEnd   = s.intakes.map(Sessions.endOf).maxBy(_.toEpochMilli)
    val end       = Summary.of(projected.projection.curve, s.startedAt).backToZeroBy.getOrElse(lastEnd)
    (Tonight.at(projected, end), end)
  }
}
