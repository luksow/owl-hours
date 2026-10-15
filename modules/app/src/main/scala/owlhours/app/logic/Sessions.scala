package owlhours.app.logic

import java.time.{Duration, Instant}
import owlhours.domain.*
import owlhours.model.Simulation

/** A night closes two hours after blood alcohol is back to ~0 (spec §7.1); the rule is derived, never stored. */
object Sessions:
  val CloseAfter: Duration = Duration.ofHours(2)

  def endOf(i: Intake): Instant = i match
    case Intake.Drink(_, start, d) => start.plusSeconds(math.round(d.unwrap * 60))
    case Intake.Water(_, start, d) => start.plusSeconds(math.round(d.unwrap * 60))
    case Intake.Meal(_, at)        => at

  def closesAt(p: Profile, s: Session): Instant =
    val lastEnd   = s.intakes.map(endOf).maxBy(_.toEpochMilli)
    val curve     = Simulation.simulate(p, s.intakes, s.startedAt, lastEnd.plusSeconds(24 * 3600), stepMinutes = 5)
    val lastAbove = curve.samples.findLast(_.bacMgDl >= Simulation.ZeroThresholdMgDl).map(_.at).getOrElse(lastEnd)
    later(lastAbove, lastEnd).plus(CloseAfter)

  def active(p: Profile, ss: Vector[Session], now: Instant): Option[Session] =
    ss.lastOption.filter(s => s.intakes.nonEmpty && !now.isAfter(closesAt(p, s)))

  def addIntake(p: Profile, ss: Vector[Session], i: Intake, now: Instant, newId: SessionId): Vector[Session] =
    active(p, ss, now) match
      case Some(s) =>
        val intakes = (s.intakes :+ i).sortBy(_.startsAt.toEpochMilli)
        ss.init :+ s.copy(startedAt = intakes.head.startsAt, intakes = intakes)
      case None => ss :+ Session(newId, i.startsAt, Vector(i))

  private def later(a: Instant, b: Instant): Instant = if a.isAfter(b) then a else b
