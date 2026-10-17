package owlhours.app.logic

import java.time.{Duration, Instant, LocalDate}
import owlhours.domain.*

class HistorySuite extends munit.FunSuite {
  private val profile = Profile(Sex.Male, LocalDate.of(1996, 1, 1), BodyWeight(80), Height(180))
  private val t0      = Instant.parse("2026-10-14T20:00:00Z")
  private def at(min: Long) = t0.plusSeconds(min * 60)
  private val pint    = DrinkSnapshot(None, "Pale lager", Millilitres(568), Abv(5), KcalPerLitre(404), Nil)
  private def drink(min: Long) = Intake.Drink(pint, at(min), Minutes(30))

  test("a night's summary: peak, drinks, standard drinks in the chosen size, duration to the last intake") {
    val s = Session(SessionId("n"), at(0), Vector(drink(0), drink(60), Intake.Water(Millilitres(250), at(100), Minutes(5))))
    val n = History.summary(profile, s, StandardDrink.Grams10)
    assertEquals(n.drinks, 2)
    assertEqualsDouble(n.standardDrinks, 2 * 568 * 0.05 * 0.789 / 10, 1e-9)
    assertEquals(n.duration, Duration.ofMinutes(105))
    assert(n.peak.exists(_.bacMgDl > 20), n.peak.toString)
    assertEqualsDouble(History.summary(profile, s, StandardDrink.Grams8).standardDrinks, 2 * 568 * 0.05 * 0.789 / 8, 1e-9)
  }

  test("a night ended by hand lasts until its end") {
    val s = Session(SessionId("n"), at(0), Vector(drink(0)), endedAt = Some(at(240)))
    assertEquals(History.summary(profile, s, StandardDrink.Grams10).duration, Duration.ofMinutes(240))
  }

  test("a water-only night has no peak and no badges") {
    val s = Session(SessionId("n"), at(0), Vector(Intake.Water(Millilitres(500), at(0), Minutes(10))))
    val n = History.summary(profile, s, StandardDrink.Grams10)
    assertEquals((n.peak, n.drinks, n.badges), (None, 0, Nil))
  }

  test("past nights are newest first and leave out the night still open") {
    val old   = Session(SessionId("old"), at(-3 * 24 * 60), Vector(drink(-3 * 24 * 60)))
    val open  = Session(SessionId("open"), at(0), Vector(drink(0)))
    assertEquals(History.past(profile, Vector(old, open), at(30)).map(_.id), Vector(SessionId("old")))
    assertEquals(History.past(profile, Vector(old, open), at(3 * 24 * 60)).map(_.id), Vector(SessionId("open"), SessionId("old")))
  }

  test("a past night's chart runs from before the first drink to back to ~0, and ends at back to ~0") {
    val s          = Session(SessionId("n"), at(0), Vector(drink(0)))
    val (t, end)   = History.chart(profile, s)
    assert(t.viewFrom.isBefore(at(0)))
    assert(end.isAfter(at(60)) && !t.viewTo.isBefore(end), s"$end ${t.viewTo}")
  }
}
