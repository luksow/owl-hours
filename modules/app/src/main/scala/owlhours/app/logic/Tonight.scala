package owlhours.app.logic

import java.time.Instant
import owlhours.domain.*
import owlhours.model.*

final case class Tonight(projection: Projection, summary: Summary, impairment: Impairment, viewFrom: Instant, viewTo: Instant)

/** The simulated night; its window does not move with `now`, so it is recomputed only when the inputs change. */
final case class Projected(projection: Projection, from: Instant, to: Instant)

object Tonight:
  val StepMinutes: Int = 2

  /** Expensive (three simulations): depends on `now` only through the hour when there is no night yet. */
  def project(p: Profile, session: Option[Session], now: Instant): Projected =
    val intakes = session.map(_.intakes).getOrElse(Vector.empty)
    val from = intakes.map(_.startsAt).minByOption(_.toEpochMilli) match
      case Some(first) => truncate(first, 60).minusSeconds(30 * 60)
      case None        => truncate(now, 3600).minusSeconds(3600)
    val lastEnd = intakes.map(Sessions.endOf).maxByOption(_.toEpochMilli).getOrElse(from.plusSeconds(3600))
    val to      = lastEnd.plusSeconds(10 * 3600)
    Projected(Simulation.project(p, intakes, from, to, StepMinutes), from, to)

  /** Cheap: reads the projection at `now`. */
  def at(pr: Projected, now: Instant): Tonight =
    val minute  = truncate(now, 60)
    val summary = Summary.of(pr.projection.curve, now)
    val viewTo  = summary.backToZeroBy.map(_.plusSeconds(30 * 60)).fold(minute.plusSeconds(3600))(later(_, minute.plusSeconds(3600)))
    Tonight(pr.projection, summary, Impairment.fromMgDl(summary.currentMgDl), pr.from, if viewTo.isAfter(pr.to) then pr.to else viewTo)

  def of(p: Profile, session: Option[Session], now: Instant): Tonight = at(project(p, session, now), now)

  private def truncate(t: Instant, seconds: Long): Instant = Instant.ofEpochSecond(t.getEpochSecond / seconds * seconds)
  private def later(a: Instant, b: Instant): Instant = if a.isAfter(b) then a else b
