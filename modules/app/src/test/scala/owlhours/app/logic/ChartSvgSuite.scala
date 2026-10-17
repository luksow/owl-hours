package owlhours.app.logic

import java.time.{Instant, LocalDate}
import owlhours.domain.*
import owlhours.model.*

class ChartSvgSuite extends munit.FunSuite {
  private val profile       = Profile(Sex.Female, LocalDate.of(1998, 1, 1), BodyWeight(62), Height(165))
  private val t0            = Instant.parse("2026-10-14T20:00:00Z")
  private def at(min: Long) = t0.plusSeconds(min * 60)
  private val wine          = DrinkSnapshot(None, "Red wine", Millilitres(175), Abv(13.5), KcalPerLitre(860), Nil)
  private val session = Session(
    SessionId("a"),
    at(0),
    Vector(Intake.Drink(wine, at(0), Minutes(20)), Intake.Drink(wine, at(30), Minutes(20)))
  )
  private val label = (t: Instant) => t.toString.substring(11, 16)

  private def svg(now: Instant, markers: List[Marker] = Nil) =
    ChartSvg.render(Tonight.of(profile, Some(session), now), now, markers, _ => 0, label)

  test("an svg with the data-space gradient, a band, a past tube and a dashed projection") {
    val s = svg(at(60))
    assert(s.startsWith("""<svg viewBox="0 0 358 232""""), s.take(80))
    assert(s.contains("""gradientUnits="userSpaceOnUse""""))
    assert(s.contains("""stop offset="0.6667" stop-color="#FFB03B""""), "amber stop at 0.8 / 1.2")
    assert(s.contains("""class="oh-band""""))
    assert(s.contains("""class="oh-past""""))
    assert(s.contains("""class="oh-projection" stroke-dasharray="5 6""""))
    assert(s.contains("""class="oh-now""""))
  }

  test("grid lines every 0.5 ‰ from 0, every 1 ‰ on a tall scale; all styled the same") {
    assertEquals(ChartSvg.gridSteps(1.3), List(0.0, 0.5, 1.0))
    assertEquals(ChartSvg.gridSteps(3.0), List(0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0))
    assertEquals(ChartSvg.gridSteps(4.2), List(0.0, 1.0, 2.0, 3.0, 4.0))
    val s = svg(at(60))
    val lines =
      """<g class="oh-grid"><line [^>]*>""".r.findAllIn(s).map(_.replaceAll("""y[12]="[^"]*"""", "")).toList.distinct
    assertEquals(lines.size, 1, "every grid line has the same style")
    assert(!s.contains("oh-threshold"))
  }

  test("markers inside the view are drawn on the time axis with their kind's colour") {
    val s = svg(
      at(60),
      List(Marker(at(0), MarkerKind.Wine), Marker(at(15), MarkerKind.Water), Marker(at(-600), MarkerKind.Meal))
    )
    assertEquals("""class="oh-marker"""".r.findAllIn(s).size, 2)
    assert(s.contains("""fill="none" stroke="#5CE1FF""""), "the water pictogram is an ice-blue line")
  }

  test("text is escaped before it enters the markup") {
    val s = ChartSvg.render(Tonight.of(profile, Some(session), at(60)), at(60), Nil, _ => 0, _ => """<b>"&'""")
    assert(!s.contains("<b>"))
    assert(s.contains("&lt;b&gt;&quot;&amp;&#39;"))
  }

  test("Markup.escape covers the five special characters") {
    assertEquals(
      Markup.escape("""<a href="x">Tom & Jerry's</a>"""),
      "&lt;a href=&quot;x&quot;&gt;Tom &amp; Jerry&#39;s&lt;/a&gt;"
    )
  }

  test("hour ticks are placed on local hours (UTC+5:30)") {
    val seen = scala.collection.mutable.ListBuffer.empty[Instant]
    val t0   = Tonight.of(profile, Some(session), at(60))
    ChartSvg.render(t0, at(60), Nil, _ => 330, t => { seen += t; "x" })
    seen --= t0.summary.peak.map(_.at) // the peak label also names its time; only the hour ticks are checked
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
    val ms = Marker.of(
      Seq(
        Intake.Drink(wine, at(0), Minutes(20)),
        Intake.Water(Millilitres(250), at(5), Minutes(5)),
        Intake.Meal(Kcal(500), at(10))
      )
    )
    assertEquals(ms.map(_.at), List(at(0), at(5), at(10)))
  }

  test("markers are neon pictograms on the time axis: drinks on the top row, water and food on the second") {
    val s = svg(
      at(60),
      List(Marker(at(0), MarkerKind.Beer), Marker(at(1), MarkerKind.Water), Marker(at(30), MarkerKind.Shot))
    )
    assertEquals("""class="oh-marker"""".r.findAllMatchIn(s).size, 3)
    val rows =
      """class="oh-marker" transform="translate\([\d.]+ ([\d.]+)\)"""".r.findAllMatchIn(s).map(_.group(1)).toList
    assertEquals(rows.distinct.size, 2)
    assertEquals(rows(0), rows(2), "both drinks share the top row")
  }

  test("marker rows by kind: drinks on top, water and food below; close markers in a row are nudged apart") {
    val placed = ChartSvg.rows(
      List(100.0 -> MarkerKind.Beer, 104.0 -> MarkerKind.Wine, 102.0 -> MarkerKind.Water, 160.0 -> MarkerKind.Meal)
    )
    assertEquals(placed.map(_._2), List(0, 0, 1, 1))
    assertEquals(placed(0)._1, 100.0)
    assertEquals(placed(1)._1, 114.0) // nudged right by the marker gap
    assertEquals(placed(2)._1, 102.0)
  }

  test("the now dot sits where the curve meets the now line; the now pill is on the time axis") {
    val s = svg(at(60))
    assert(s.contains("""class="oh-now-dot""""))
    val pillY = """class="oh-now-pill"><rect [^>]*?y="([\d.]+)"""".r.findFirstMatchIn(s).map(_.group(1).toDouble)
    assert(pillY.exists(_ > 200), s"pill at $pillY")
  }

  test("peak and back-to dots: amber at the peak with its label, ice on the baseline at the back-to time") {
    val t = Tonight.of(profile, Some(session), at(40))
    val s = ChartSvg.render(t, at(40), Nil, _ => 0, label)
    assert(s.contains("""class="oh-peak""""))
    val pk = t.summary.peak.get
    assert(s.contains(s"${Display.peak(pk.bacMgDl, BacUnit.Permille)} · ${label(pk.at)}"), "peak label")
    assert("""<g class="oh-back"[^>]*>.*?cy="170.0"""".r.findFirstIn(s).isDefined, "back-to dot on the baseline")
  }

  test("history: no now line, dot or pill; peak and back-to dots stay") {
    val t = Tonight.of(profile, Some(session), at(300))
    val s = ChartSvg.render(t, at(300), Nil, _ => 0, label, showNow = false)
    assert(!s.contains("oh-now"))
    assert(s.contains("oh-peak") && s.contains("oh-back"))
  }

  test("no night: no peak or back-to dot") {
    val t = Tonight.of(profile, None, at(0))
    val s = ChartSvg.render(t, at(0), Nil, _ => 0, label)
    assert(!s.contains("oh-peak") && !s.contains("oh-back"))
  }

  test("hour ticks stay on local hours across a DST change") {
    val change = at(120)
    val offset = (t: Instant) => if t.isBefore(change) then 60 else 120
    val seen   = scala.collection.mutable.ListBuffer.empty[Instant]
    val t0     = Tonight.of(profile, Some(session), at(60))
    ChartSvg.render(t0, at(60), Nil, offset, t => { seen += t; "x" })
    seen --= t0.summary.peak.map(_.at) // the peak label also names its time; only the hour ticks are checked
    assert(seen.size >= 2)
    assert(seen.forall(t => (t.getEpochSecond / 60 + offset(t)) % 60 == 0), seen.toString)
  }

  test("review: a grid label near the top moves below its line, clear of the now pill") {
    val peak    = 2 * BacUnit.MgDlPerPermille / 1.15 + 1e-6 // puts the 2‰ line on the chart's top edge
    val samples = (0 to 120 by 2).map(m => BacSample(at(m), peak, 0.0)).toVector
    val t = Tonight(
      Projection(Curve(samples), samples.map(_.bacMgDl), samples.map(_.bacMgDl)),
      Summary(peak, Trend.Steady, None, None),
      Impairment.Drunk,
      at(0),
      at(120),
      capped = false
    )
    val ys = """<g class="oh-grid">.*?<text x="[^"]+" y="([^"]+)"""".r
      .findAllMatchIn(ChartSvg.render(t, at(60), Nil, _ => 0, label))
      .map(_.group(1).toDouble)
      .toList
    assert(ys.size == ChartSvg.gridSteps(2.0).size && ys.forall(_ >= 16), ys.toString)
  }

  test("a past night's chart has its own label and no now marker") {
    val s = ChartSvg.render(
      Tonight.of(profile, Some(session), at(60)),
      at(60),
      Nil,
      _ => 0,
      label,
      label = "Blood alcohol estimate on Tue 14 Oct & co",
      showNow = false
    )
    assert(s.contains("""aria-label="Blood alcohol estimate on Tue 14 Oct &amp; co""""), s.take(200))
    assert(!s.contains("oh-now"))
  }

  test("final review: the peak label is the chip's text, in every unit") {
    val t  = Tonight.of(profile, Some(session), at(40))
    val pk = t.summary.peak.get
    for u <- BacUnit.values do {
      val s = ChartSvg.render(t, at(40), Nil, _ => 0, label, unit = u)
      assert(s.contains(s">${Markup.escape(Display.peak(pk.bacMgDl, u))} · ${label(pk.at)}<"), s"$u")
    }
  }

  test("final review: a 30-hour chart draws at most eight hour labels") {
    val samples = (0 to 30 * 60 by 10).map(m => BacSample(at(m), 50.0, 0.0)).toVector
    val t = Tonight(
      Projection(Curve(samples), samples.map(_.bacMgDl), samples.map(_.bacMgDl)),
      Summary(50.0, Trend.Steady, None, None),
      Impairment.Tipsy,
      at(0),
      at(30 * 60),
      capped = false
    )
    val ticks = """font-size="11"""".r.findAllIn(ChartSvg.render(t, at(60), Nil, _ => 0, label)).size
    assert(ticks >= 4 && ticks <= 8, s"$ticks hour labels")
  }
}
