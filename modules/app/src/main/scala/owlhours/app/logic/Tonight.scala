package owlhours.app.logic

import java.time.Instant
import owlhours.domain.*
import owlhours.model.*

final case class Tonight(projection: Projection, summary: Summary, impairment: Impairment, viewFrom: Instant, viewTo: Instant)

object Tonight:
  val StepMinutes: Int = 2

  def of(p: Profile, session: Option[Session], now: Instant): Tonight =
    val minute  = truncate(now)
    val intakes = session.map(_.intakes).getOrElse(Vector.empty)
    val from    = intakes.map(_.startsAt).minByOption(_.toEpochMilli).map(s => truncate(s).minusSeconds(30 * 60))
      .getOrElse(minute.minusSeconds(3600))
    val lastEnd = intakes.map(Sessions.endOf).maxByOption(_.toEpochMilli).getOrElse(minute)
    val to      = later(lastEnd, minute).plusSeconds(10 * 3600)
    val proj    = Simulation.project(p, intakes, from, to, StepMinutes)
    val summary = Summary.of(proj.curve, now)
    val viewTo  = summary.backToZeroBy.map(_.plusSeconds(30 * 60)).fold(minute.plusSeconds(3600))(later(_, minute.plusSeconds(3600)))
    Tonight(proj, summary, Impairment.fromMgDl(summary.currentMgDl), from, if viewTo.isAfter(to) then to else viewTo)

  private def truncate(t: Instant): Instant = Instant.ofEpochSecond(t.getEpochSecond / 60 * 60)
  private def later(a: Instant, b: Instant): Instant = if a.isAfter(b) then a else b
