package owlhours.model

import java.time.{Instant, LocalDate}
import owlhours.domain.*

class BandSuite extends munit.FunSuite {
  private val profile = Profile(Sex.Female, LocalDate.of(1998, 1, 1), BodyWeight(65), Height(168))
  private val t0      = Instant.parse("2026-10-14T20:00:00Z")
  private val wine    = DrinkSnapshot(None, "Red wine", Millilitres(150), Abv(13), KcalPerLitre(830), Nil)
  private val intakes =
    Seq(Intake.Drink(wine, t0.plusSeconds(600), Minutes(20)), Intake.Drink(wine, t0.plusSeconds(4200), Minutes(20)))

  test("band contains the central curve and has real width at the peak") {
    val p = Simulation.project(profile, intakes, t0, t0.plusSeconds(8 * 3600), stepMinutes = 5)
    assertEquals(p.low.size, p.curve.samples.size)
    for (s, i) <- p.curve.samples.zipWithIndex do
      assert(p.low(i) <= s.bacMgDl + 1e-9 && s.bacMgDl <= p.high(i) + 1e-9, s"sample $i")
    val peak = p.curve.samples.indices.maxBy(i => p.curve.samples(i).bacMgDl)
    assert(p.high(peak) - p.low(peak) > 0.1 * p.curve.samples(peak).bacMgDl)
  }

  test("metaboliser corners scale exactly the documented parameters") {
    val base = Params.published
    val slow = Params.slowMetaboliser(base)
    assertEqualsDouble(slow.vmaxADH1, base.vmaxADH1 * 0.8, 1e-15)
    assertEqualsDouble(slow.vmaxCYP2E1, base.vmaxCYP2E1 * 0.8, 1e-15)
    assertEqualsDouble(slow.kEtOHUptake, base.kEtOHUptake * 1.3, 1e-15)
    assertEqualsDouble(slow.vmaxGastric, base.vmaxGastric * 1.3, 1e-15)
    assertEquals(
      slow.copy(
        vmaxADH1 = base.vmaxADH1,
        vmaxCYP2E1 = base.vmaxCYP2E1,
        kEtOHUptake = base.kEtOHUptake,
        vmaxGastric = base.vmaxGastric
      ),
      base
    )
    val fast = Params.fastMetaboliser(base)
    assertEqualsDouble(fast.vmaxADH1, base.vmaxADH1 * 1.2, 1e-15)
    assertEqualsDouble(fast.kEtOHUptake, base.kEtOHUptake * 0.7, 1e-15)
  }
}
