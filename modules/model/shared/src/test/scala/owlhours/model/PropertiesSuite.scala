package owlhours.model

import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

class PropertiesSuite extends munit.ScalaCheckSuite {
  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(25)

  private val anth = for {
    sex    <- Gen.oneOf(0.0, 1.0)
    weight <- Gen.choose(45.0, 130.0)
    height <- Gen.choose(1.50, 2.00)
    age    <- Gen.choose(18.0, 80.0)
  }
  yield Anthropometrics(sex, weight, height, age)

  private val drink = for {
    start <- Gen.choose(5.0, 300.0)
    dur   <- Gen.choose(0.5, 60.0)
    vol   <- Gen.choose(0.03, 0.6)
    abv   <- Gen.choose(0.0, 45.0)
    kcal  <- Gen.choose(0.0, 2500.0)
  }
  yield DrinkInput(start, dur, vol, abv, kcal)

  private val meal = for {
    at   <- Gen.choose(5.0, 300.0)
    kcal <- Gen.choose(100.0, 1500.0)
  }
  yield MealInput(at, kcal)

  private val night = for {
    a      <- anth
    drinks <- Gen.choose(1, 6).flatMap(Gen.listOfN(_, drink))
    meals  <- Gen.choose(0, 2).flatMap(Gen.listOfN(_, meal))
  }
  yield (a, Schedule(drinks, meals))

  private val ethanolMgPerLPerPercent = 789.1 * 10

  private def bodyEthanolMg(s: Array[Double], v: Volumes): Double = {
    import State.*
    val tissue = v.totalBodyWater - v.blood - v.liver + s(TissueExtraWater)
    s(StomachConc) * s(VolStomach) + s(EtOHPool) + s(MassEtOHIntestines) + s(BloodConcCentral) * v.bloodCentral +
      s(BloodConcPeripheral) * v.bloodPeripheral + s(TissueConc) * tissue + s(LiverConc) * v.liver + s(UrineMassEtOH)
  }

  property("blood alcohol is finite and never negative") {
    forAll(night) { (a, sched) =>
      val r = Engine.run(a, sched, 0, 720, 10)
      (0 until r.size).forall(i => r.bacMgDl(i).isFinite && r.bacMgDl(i) >= -1e-9)
    }
  }

  property("the body never holds more ethanol than was drunk") {
    forAll(night) { (a, sched) =>
      val r       = Engine.run(a, sched, 0, 720, 10)
      val v       = Volumes.of(a, Params.published)
      val drunkMg = (t: Double) =>
        sched.drinks.map(d => math.min(math.max(t - d.startMin, 0), d.durationMin) * d.rateLPerMin * d.abvPercent * ethanolMgPerLPerPercent).sum
      // 2 % slack: tissue concentration is diluted/concentrated as tissue water changes, which the model
      // does not mass-correct. Unit errors (×10, ×100) would still fail this by a wide margin.
      (0 until r.size).forall(i => bodyEthanolMg(r.states(i), v) <= drunkMg(r.times(i)) * 1.02 + 50.0)
    }
  }

  property("a stronger drink (same volume and kcal) never gives a lower peak") {
    forAll(anth, drink) { (a, d) =>
      val peak = (dd: DrinkInput) =>
        val r = Engine.run(a, Schedule(Seq(dd), Nil), 0, 600, 5)
        (0 until r.size).map(r.bacMgDl).max
      peak(d.copy(abvPercent = math.min(d.abvPercent * 1.5 + 1, 60))) >= peak(d) - 1e-6
    }
  }

  private val moderateNight =
    night.suchThat((_, s) => s.drinks.map(d => d.volumeL * d.abvPercent * 7.891).sum <= 120) // ≤ 120 g ethanol

  property("blood alcohol returns close to zero a day after a moderate night") {
    forAll(moderateNight) { (a, sched) =>
      val r = Engine.run(a, sched, 0, 300 + 24 * 60, 30)
      r.bacMgDl(r.size - 1) < 1.0
    }
  }

  test("a 12-hour night with 10 drinks and the variation band runs within budget") {
    import java.time.{Instant, LocalDate}
    import owlhours.domain.*
    val profile = Profile(Sex.Male, LocalDate.of(1991, 1, 1), BodyWeight(85), Height(182))
    val t0      = Instant.parse("2026-10-14T19:00:00Z")
    val beer    = DrinkSnapshot(None, "IPA", Millilitres(500), Abv(6.5), KcalPerLitre(560), Nil)
    val intakes = (0 until 10).map(i => Intake.Drink(beer, t0.plusSeconds(i * 40L * 60), Minutes(25))) :+
      Intake.Meal(Kcal(900), t0.plusSeconds(120 * 60))
    Simulation.project(profile, intakes, t0, t0.plusSeconds(12 * 3600)) // warm-up (JIT on JVM)
    val start   = System.nanoTime()
    val p       = Simulation.project(profile, intakes, t0, t0.plusSeconds(12 * 3600))
    val elapsed = (System.nanoTime() - start) / 1e6
    println(f"project(12 h, 10 drinks, 3 runs): $elapsed%.1f ms")
    assert(p.curve.samples.size == 12 * 60 + 1)
    assert(elapsed < 1000, s"took $elapsed ms")
  }
}
