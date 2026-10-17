package owlhours.app.logic

import java.time.{Duration, Instant}
import owlhours.domain.*
import owlhours.model.Simulation

/**
 * A night closes two hours after blood alcohol is back to ~0 (spec §7.1). Nights are regrouped by this rule after
 * every change; ids are derived from the first intake, so nothing stored can disagree with the intakes.
 */
object Sessions {
  val CloseAfter: Duration = Duration.ofHours(2)

  /** `closesAt` looks at most a day past the last intake, so a night can close no later than this. */
  private val LatestClose: Duration = Duration.ofHours(24).plus(CloseAfter)

  def endOf(i: Intake): Instant = i match {
    case Intake.Drink(_, start, d) => start.plusSeconds(math.round(d.unwrap * 60))
    case Intake.Water(_, start, d) => start.plusSeconds(math.round(d.unwrap * 60))
    case Intake.Meal(_, at)        => at
  }

  /** A night ended by hand closes at its end; otherwise two hours after blood alcohol is back to ~0. */
  def closesAt(p: Profile, s: Session): Instant = s.endedAt.getOrElse(ruleClose(p, s))

  private def ruleClose(p: Profile, s: Session): Instant = {
    val lastEnd   = s.intakes.map(endOf).maxBy(_.toEpochMilli)
    val curve     = Simulation.simulate(p, s.intakes, s.startedAt, lastEnd.plusSeconds(24 * 3600), stepMinutes = 5)
    val lastAbove = curve.samples.findLast(_.bacMgDl >= Simulation.ZeroThresholdMgDl).map(_.at).getOrElse(lastEnd)
    later(lastAbove, lastEnd).plus(CloseAfter)
  }

  /** A night ended by hand is closed at once, even to a clock that ticked just before the tap. */
  def active(p: Profile, ss: Vector[Session], now: Instant): Option[Session] =
    ss.lastOption.filter(s => s.intakes.nonEmpty && s.endedAt.isEmpty && !now.isAfter(closesAt(p, s)))

  /**
   * Only once the estimate is back to ~0: a drink after a manual end starts a new night, and a new night started
   * while alcohol is still in the blood would leave it out of Tonight.
   */
  def canEnd(p: Profile, s: Session, at: Instant): Boolean =
    s.endedAt.isEmpty && {
      val close = ruleClose(p, s)
      !at.isAfter(close) && !at.isBefore(close.minus(CloseAfter))
    }

  def end(p: Profile, ss: Vector[Session], at: Instant): Vector[Session] =
    ss.lastOption.filter(canEnd(p, _, at)).fold(ss)(s => ss.init :+ s.copy(endedAt = Some(at)))

  def regroupAll(p: Profile, ss: Vector[Session]): Vector[Session] = group(p, ss.flatMap(_.intakes), ss.flatMap(_.endedAt))

  /**
   * Groups intakes into nights: a new night starts when an intake begins after the current night has closed by rule,
   * or after a manual end. An end belongs to the night it falls in, and holds only while that night is back to ~0 at
   * it (as `canEnd` requires): a drink re-timed to before the end reopens the night. Other ends are dropped.
   */
  def group(p: Profile, intakes: Vector[Intake], ends: Vector[Instant] = Vector.empty): Vector[Session] = {
    val sortedEnds = ends.sortBy(_.toEpochMilli)
    def endBefore(s: Session, limit: Instant) = {
      val close = ruleClose(p, s)
      sortedEnds.find(e => !e.isBefore(close.minus(CloseAfter)) && e.isBefore(limit))
    }
    def open(i: Intake) = Session(SessionId(s"night-${i.startsAt.toEpochMilli}"), i.startsAt, Vector(i))
    val (done, current) = intakes.sortBy(_.startsAt.toEpochMilli).foldLeft((Vector.empty[Session], Option.empty[Session])) {
      case ((done, None), i) => (done, Some(open(i)))
      case ((done, Some(s)), i) =>
        val close = ruleClose(p, s)
        if i.startsAt.isAfter(close) then (done :+ s.copy(endedAt = endBefore(s, close)), Some(open(i)))
        else
          endBefore(s, i.startsAt) match {
            case Some(e) => (done :+ s.copy(endedAt = Some(e)), Some(open(i)))
            case None    => (done, Some(s.copy(intakes = s.intakes :+ i)))
          }
    }
    done ++ current.map(s => s.copy(endedAt = endBefore(s, ruleClose(p, s))))
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
  private def regroup(p: Profile, ss: Vector[Session], changedAt: Instant)(f: Vector[Intake] => Vector[Intake]) = {
    val (keep, redo) = ss.span(s => s.intakes.map(endOf).maxBy(_.toEpochMilli).plus(LatestClose).isBefore(changedAt))
    keep ++ group(p, f(redo.flatMap(_.intakes)), redo.flatMap(_.endedAt))
  }

  private def removeFirst(xs: Vector[Intake], x: Intake): Vector[Intake] = {
    val i = xs.indexOf(x)
    if i < 0 then xs else xs.patch(i, Nil, 1)
  }

  private def later(a: Instant, b: Instant): Instant   = if a.isAfter(b) then a else b
  private def earlier(a: Instant, b: Instant): Instant = if a.isBefore(b) then a else b
}
