package owlhours.app.logic

import java.time.{Instant, LocalDate}
import owlhours.catalogue.*
import owlhours.domain.*
import owlhours.model.{Impairment, Trend}

class TonightSuite extends munit.FunSuite:
  private val profile = Profile(Sex.Female, LocalDate.of(1998, 1, 1), BodyWeight(62), Height(165))
  private val t0      = Instant.parse("2026-10-14T20:00:00Z")
  private def at(min: Long) = t0.plusSeconds(min * 60)
  private val wine    = DrinkSnapshot(None, "Red wine", Millilitres(175), Abv(13.5), KcalPerLitre(860), Nil)

  test("no night yet: zero, steady, sober, a one-hour window around now") {
    val t = Tonight.of(profile, None, at(0))
    assertEquals(t.summary.currentMgDl, 0.0)
    assertEquals(t.summary.trend, Trend.Steady)
    assertEquals(t.impairment, Impairment.Sober)
    assert(!t.viewFrom.isAfter(at(0)) && t.viewTo.isAfter(at(0)))
  }

  test("two glasses of wine: rising after drinking, a peak and a back-to-~0 time inside the view") {
    val s = Session(SessionId("a"), at(0), Vector(Intake.Drink(wine, at(0), Minutes(20)), Intake.Drink(wine, at(30), Minutes(20))))
    val t = Tonight.of(profile, Some(s), at(40))
    assert(t.summary.currentMgDl > 10, t.summary.toString)
    assertEquals(t.summary.trend, Trend.Rising)
    val back = t.summary.backToZeroBy.get
    assert(back.isAfter(at(120)))
    assert(!t.viewTo.isBefore(back), s"view ends ${t.viewTo}, back to ~0 at $back")
    assertEquals(t.viewFrom, at(-30))
  }

  test("durations follow the serve type") {
    def style(cat: Category) = Style(CatalogueId("x"), "x", cat, Abv(5), Abv(4), Abv(6), CarbsPerLitre(30), Nil, Nil, "t", false)
    def product(cat: Category) = Product(CatalogueId("y"), "y", cat, Abv(40), KcalPerLitre(2210), true, Nil, Nil, "t", false)
    assertEquals(Durations.default(style(Category.Beer), Millilitres(568)), Minutes(30))
    assertEquals(Durations.default(style(Category.Beer), Millilitres(330)), Minutes(20))
    assertEquals(Durations.default(style(Category.Wine), Millilitres(150)), Minutes(20))
    assertEquals(Durations.default(product(Category.Spirit), Millilitres(40)), Minutes(1))
    assertEquals(Durations.default(product(Category.Mixer), Millilitres(200)), Minutes(5))
  }

  test("BAC is formatted in the chosen unit") {
    assertEquals(Display.bac(65.1, 0.31, BacUnit.Permille), "0.62‰")
    assertEquals(Display.bac(65.1, 0.31, BacUnit.PercentBac), "0.065%")
    assertEquals(Display.bac(65.1, 0.31, BacUnit.MgPer100ml), "65 mg/100 ml")
    assertEquals(Display.bac(65.1, 0.31, BacUnit.BreathMgPerL), "0.31 mg/L")
    assertEquals(Display.bac(0, 0, BacUnit.Permille), "0.00‰")
    assertEquals(Display.impairment(Impairment.WarmingUp), "Warming up")
  }
