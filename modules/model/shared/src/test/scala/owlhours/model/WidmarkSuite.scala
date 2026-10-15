package owlhours.model

import java.time.{Instant, LocalDate}
import owlhours.domain.*

class WidmarkSuite extends munit.FunSuite:
  private val male   = Profile(Sex.Male, LocalDate.of(1996, 1, 1), BodyWeight(80), Height(180))
  private val female = Profile(Sex.Female, LocalDate.of(1996, 1, 1), BodyWeight(80), Height(180))
  private val t0     = Instant.parse("2026-10-14T20:00:00Z")
  private def at(min: Long) = t0.plusSeconds(min * 60)
  private val pint   = DrinkSnapshot(None, "Lager", Millilitres(568), Abv(5), KcalPerLitre(404), Nil)
  private val times  = Vector.tabulate(301)(i => at(i))

  test("one pint for an 80 kg man: absorbed over 30 min, peak ≈ 22.4 g / (0.68 × 80) minus elimination") {
    val c    = Widmark.curveMgDl(male, Seq(Intake.Drink(pint, at(0), Minutes(30))), times)
    val peak = c.max
    // 22.41 g / 54.4 kg = 0.412 ‰; minus 0.05 ‰ eliminated over 30 min ≈ 0.362 ‰ ≈ 38 mg/dL
    assertEqualsDouble(peak, 0.362 * 105, 1.5)
    assertEquals(c.indexOf(peak), 30)
    assert(c.last == 0.0 || c.last < c(30))
  }

  test("a woman of the same weight gets a higher estimate (r = 0.55)") {
    val drinks = Seq(Intake.Drink(pint, at(0), Minutes(30)))
    assert(Widmark.curveMgDl(female, drinks, times).max > Widmark.curveMgDl(male, drinks, times).max)
  }

  test("water and meals add nothing; no intakes is zero") {
    val c = Widmark.curveMgDl(male, Seq(Intake.Water(Millilitres(500), at(0), Minutes(5)), Intake.Meal(Kcal(900), at(0))), times)
    assert(c.forall(_ == 0.0))
  }

  test("drinks before the first sample are counted") {
    val c = Widmark.curveMgDl(male, Seq(Intake.Drink(pint, at(-60), Minutes(30))), times)
    assert(c.head > 20, c.head.toString)
  }

  test("the band's upper edge includes Widmark; the central curve is unchanged") {
    val drinks = Seq(Intake.Drink(pint, at(0), Minutes(30)), Intake.Drink(pint, at(30), Minutes(30)))
    val p      = Simulation.project(male, drinks, t0, at(600))
    val alone  = Simulation.simulate(male, drinks, t0, at(600))
    assertEquals(p.curve, alone)
    val w = Widmark.curveMgDl(male, drinks, p.curve.samples.map(_.at))
    for i <- p.curve.samples.indices do
      assert(p.high(i) >= w(i) - 1e-9 && p.high(i) >= p.curve.samples(i).bacMgDl - 1e-9, s"sample $i")
  }
