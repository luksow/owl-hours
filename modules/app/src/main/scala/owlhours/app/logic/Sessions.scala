package owlhours.app.logic

import java.time.{Duration, Instant}
import owlhours.domain.*
import owlhours.model.Simulation

/**
 * A night closes two hours after blood alcohol is back to ~0 (spec §7.1). Nights are regrouped by this rule after
 * every change; ids are derived from the first intake, so nothing stored can disagree with the intakes.
 */
object Sessions:
  val CloseAfter: Duration = Duration.ofHours(2)

  /** `closesAt` looks at most a day past the last intake, so a night can close no later than this. */
  private val LatestClose: Duration = Duration.ofHours(24).plus(CloseAfter)

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

  /** Groups intakes into nights: a new night starts when an intake begins after the current night has closed. */
  def group(p: Profile, intakes: Vector[Intake]): Vector[Session] =
    intakes.sortBy(_.startsAt.toEpochMilli).foldLeft(Vector.empty[Session]) { (acc, i) =>
      acc.lastOption match
        case Some(s) if !i.startsAt.isAfter(closesAt(p, s)) => acc.init :+ s.copy(intakes = s.intakes :+ i)
        case _ => acc :+ Session(SessionId(s"night-${i.startsAt.toEpochMilli}"), i.startsAt, Vector(i))
    }

  def add(p: Profile, ss: Vector[Session], i: Intake): Vector[Session] =
    regroup(p, ss, i.startsAt)(_ :+ i)

  /** Entries are addressed by value: an entry that is no longer there (a stale tap) leaves the nights unchanged. */
  def replace(p: Profile, ss: Vector[Session], old: Intake, updated: Intake): Vector[Session] =
    if !contains(ss, old) then ss else regroup(p, ss, earlier(old.startsAt, updated.startsAt))(xs => removeFirst(xs, old) :+ updated)

  def delete(p: Profile, ss: Vector[Session], old: Intake): Vector[Session] =
    if !contains(ss, old) then ss else regroup(p, ss, old.startsAt)(removeFirst(_, old))

  private def contains(ss: Vector[Session], i: Intake): Boolean = ss.exists(_.intakes.contains(i))

  /**
   * Grouping is a left fold over time, so nights that closed before the change are unaffected and kept as they are.
   * The cheap upper bound on closing avoids simulating every past night.
   */
  private def regroup(p: Profile, ss: Vector[Session], changedAt: Instant)(f: Vector[Intake] => Vector[Intake]) =
    val (keep, redo) = ss.span(s => s.intakes.map(endOf).maxBy(_.toEpochMilli).plus(LatestClose).isBefore(changedAt))
    keep ++ group(p, f(redo.flatMap(_.intakes)))

  private def removeFirst(xs: Vector[Intake], x: Intake): Vector[Intake] =
    val i = xs.indexOf(x)
    if i < 0 then xs else xs.patch(i, Nil, 1)

  private def later(a: Instant, b: Instant): Instant   = if a.isAfter(b) then a else b
  private def earlier(a: Instant, b: Instant): Instant = if a.isBefore(b) then a else b
