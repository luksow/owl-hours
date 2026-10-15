package owlhours.app.logic

import java.time.{Instant, LocalDate}
import owlhours.domain.*

class ChartSvgSuite extends munit.FunSuite:
  private val profile = Profile(Sex.Female, LocalDate.of(1998, 1, 1), BodyWeight(62), Height(165))
  private val t0      = Instant.parse("2026-10-14T20:00:00Z")
  private def at(min: Long) = t0.plusSeconds(min * 60)
  private val wine    = DrinkSnapshot(None, "Red wine", Millilitres(175), Abv(13.5), KcalPerLitre(860), Nil)
  private val session = Session(SessionId("a"), at(0), Vector(Intake.Drink(wine, at(0), Minutes(20)), Intake.Drink(wine, at(30), Minutes(20))))
  private val label   = (t: Instant) => t.toString.substring(11, 16)

  private def svg(now: Instant, markers: List[Marker] = Nil) =
    ChartSvg.render(Tonight.of(profile, Some(session), now), now, markers, _ => 0, label)

  test("an svg with the data-space gradient, a band, a past tube and a dashed projection") {
    val s = svg(at(60))
    assert(s.startsWith("""<svg viewBox="0 0 358 220""""), s.take(80))
    assert(s.contains("""gradientUnits="userSpaceOnUse""""))
    assert(s.contains("""stop offset="0.6667" stop-color="#FFB03B""""), "amber stop at 0.8 / 1.2")
    assert(s.contains("""class="oh-band""""))
    assert(s.contains("""class="oh-past""""))
    assert(s.contains("""class="oh-projection" stroke-dasharray="5 6""""))
    assert(s.contains("""class="oh-now""""))
  }

  test("no horizontal threshold line is drawn (a 0.5 ‰ line would read as a target)") {
    assert(!svg(at(60)).contains("oh-threshold"))
  }

  test("markers inside the view are drawn on the time axis with their kind's colour") {
    val s = svg(at(60), List(Marker(at(0), MarkerKind.Drink), Marker(at(15), MarkerKind.Water), Marker(at(-600), MarkerKind.Meal)))
    assertEquals("""class="oh-marker"""".r.findAllIn(s).size, 2)
    assert(s.contains("""fill="#5CE1FF""""))
  }

  test("text is escaped before it enters the markup") {
    val s = ChartSvg.render(Tonight.of(profile, Some(session), at(60)), at(60), Nil, _ => 0, _ => """<b>"&'""")
    assert(!s.contains("<b>"))
    assert(s.contains("&lt;b&gt;&quot;&amp;&#39;"))
  }

  test("Markup.escape covers the five special characters") {
    assertEquals(Markup.escape("""<a href="x">Tom & Jerry's</a>"""), "&lt;a href=&quot;x&quot;&gt;Tom &amp; Jerry&#39;s&lt;/a&gt;")
  }

  test("hour ticks are placed on local hours (UTC+5:30)") {
    val seen = scala.collection.mutable.ListBuffer.empty[Instant]
    ChartSvg.render(Tonight.of(profile, Some(session), at(60)), at(60), Nil, _ => 330, t => { seen += t; "x" })
    assert(seen.nonEmpty)
    assert(seen.forall(t => (t.getEpochSecond / 60 + 330) % 60 == 0), seen.toString)
  }

  test("a ghost curve is drawn faintly when given, and not otherwise") {
    val t = Tonight.of(profile, Some(session), at(60))
    assert(!ChartSvg.render(t, at(60), Nil, _ => 0, label).contains("oh-ghost"))
    val withGhost = ChartSvg.render(t, at(60), Nil, _ => 0, label, ghost = Some(t.projection.curve))
    assert(withGhost.contains("""class="oh-ghost""""))
  }

  test("markers come from intakes: a drink, a water, a meal") {
    val ms = Marker.of(Seq(Intake.Drink(wine, at(0), Minutes(20)), Intake.Water(Millilitres(250), at(5), Minutes(5)), Intake.Meal(Kcal(500), at(10))))
    assertEquals(ms.map(_.at), List(at(0), at(5), at(10)))
  }
