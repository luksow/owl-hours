package owlhours.model

import java.time.Instant
import owlhours.domain.*

class SimulationSuite extends munit.FunSuite:
  private val profile = Profile(Sex.Male, Age(30), BodyWeight(80), Height(180))
  private val t0      = Instant.parse("2026-10-14T20:00:00Z")
  private def at(min: Long) = t0.plusSeconds(min * 60)
  private val lager   = DrinkSnapshot(None, "Lager", Millilitres(500), Abv(5), KcalPerLitre(430), Nil)

  test("profile converts to model units") {
    assertEquals(Simulation.anthropometrics(profile), Anthropometrics(1, 80, 1.8, 30))
  }

  test("matches the engine for the same beer") {
    val curve  = Simulation.simulate(profile, Seq(Intake.Drink(lager, at(10), Minutes(20))), t0, at(240), stepMinutes = 5)
    val engine = Engine.run(Anthropometrics(1, 80, 1.8, 30), Schedule(Seq(DrinkInput(10, 20, 0.5, 5, 430)), Nil), 0, 240, 5)
    assertEquals(curve.samples.size, engine.size)
    assertEquals(curve.samples.head.at, t0)
    for i <- 0 until engine.size do assertEqualsDouble(curve.samples(i).bacMgDl, engine.bacMgDl(i), 1e-9)
  }

  test("intakes before `from` are included; samples start exactly at `from`") {
    val drinks = Seq(Intake.Drink(lager, at(10), Minutes(20)))
    val full   = Simulation.simulate(profile, drinks, t0, at(240), stepMinutes = 5)
    val late   = Simulation.simulate(profile, drinks, at(60), at(240), stepMinutes = 5)
    assertEquals(late.samples.head.at, at(60))
    val fullAt60 = full.samples.find(_.at == at(60)).get
    // The two runs start at different times, and the model's basal urine volume grows from the start,
    // so they differ very slightly (about 0.01 mg/dL). That is the model's behaviour, not an error.
    assertEqualsDouble(late.samples.head.bacMgDl, fullAt60.bacMgDl, 0.05)
  }

  test("a shot logged with zero duration is treated as 0.5 min, not a division by zero") {
    val shot  = DrinkSnapshot(None, "Vodka", Millilitres(40), Abv(40), KcalPerLitre(2310), Nil)
    val curve = Simulation.simulate(profile, Seq(Intake.Drink(shot, at(10), Minutes(0))), t0, at(240))
    assert(curve.samples.forall(s => s.bacMgDl.isFinite && s.bacMgDl >= 0))
    assert(curve.samples.map(_.bacMgDl).max > 5)
  }

  test("water and meals are accepted") {
    val curve = Simulation.simulate(
      profile,
      Seq(Intake.Meal(Kcal(700), at(5)), Intake.Water(Millilitres(300), at(15), Minutes(5)), Intake.Drink(lager, at(10), Minutes(20))),
      t0,
      at(240)
    )
    assert(curve.samples.map(_.bacMgDl).max > 1)
  }

  test("breath alcohol is 0 when blood alcohol is 0 (the model's intercept is not shown)") {
    val curve = Simulation.simulate(profile, Nil, t0, at(30))
    assert(curve.samples.forall(_.bracMgL == 0.0))
  }
