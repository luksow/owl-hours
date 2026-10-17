package owlhours.app.logic

import java.time.Instant
import owlhours.domain.*
import owlhours.model.*

final case class Tonight(projection: Projection, summary: Summary, impairment: Impairment, viewFrom: Instant, viewTo: Instant,
    capped: Boolean)

/** The simulated night; its window does not move with `now`, so it is recomputed only when the inputs change. */
final case class Projected(projection: Projection, from: Instant, to: Instant, capped: Boolean)

object Tonight {
  val StepMinutes: Int = 2

  /** Hours simulated after the last intake ends, tried in order until the estimate is back to ~0 (spec §4). */
  val Horizons: List[Long] = List(10, 20, 36)

  /** Expensive (three simulations per window tried): depends on `now` only through the hour when there is no night yet. */
  def project(p: Profile, session: Option[Session], now: Instant, horizons: List[Long] = Horizons): Projected = {
    val intakes = session.map(_.intakes).getOrElse(Vector.empty)
    val from = intakes.map(_.startsAt).minByOption(_.toEpochMilli) match {
      case Some(first) => truncate(first, 60).minusSeconds(30 * 60)
      case None        => truncate(now, 3600).minusSeconds(3600)
    }
    val lastEnd = intakes.map(Sessions.endOf).maxByOption(_.toEpochMilli).getOrElse(from.plusSeconds(3600))
    def run(hours: Long) = {
      val to = lastEnd.plusSeconds(hours * 3600)
      Projected(Simulation.project(p, intakes, from, to, StepMinutes), from, to, capped = false)
    }
    def backToZero(pr: Projected) = pr.projection.curve.samples.last.bacMgDl < Simulation.ZeroThresholdMgDl
    // windows are tried in order, so a light night pays for one
    @annotation.tailrec def first(hs: List[Long]): Projected = {
      val pr = run(hs.head)
      if backToZero(pr) || hs.tail.isEmpty then pr else first(hs.tail)
    }
    val last = first(horizons)
    if backToZero(last) then last else last.copy(capped = true)
  }

  /** Cheap: reads the projection at `now`. */
  def at(pr: Projected, now: Instant): Tonight = {
    val minute  = truncate(now, 60)
    val summary = Summary.of(pr.projection.curve, now)
    val viewTo  = summary.backToZeroBy.map(_.plusSeconds(30 * 60)).fold(minute.plusSeconds(3600))(later(_, minute.plusSeconds(3600)))
    // a capped night is still above ~0 at the window's end, so its chart runs all the way there
    val end = if pr.capped || viewTo.isAfter(pr.to) then pr.to else viewTo
    Tonight(pr.projection, summary, Impairment.fromMgDl(summary.currentMgDl), pr.from, end, pr.capped)
  }

  def of(p: Profile, session: Option[Session], now: Instant): Tonight = at(project(p, session, now), now)

  private def truncate(t: Instant, seconds: Long): Instant = Instant.ofEpochSecond(t.getEpochSecond / seconds * seconds)
  private def later(a: Instant, b: Instant): Instant = if a.isAfter(b) then a else b
}
