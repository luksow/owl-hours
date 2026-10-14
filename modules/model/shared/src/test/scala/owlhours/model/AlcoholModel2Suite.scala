package owlhours.model

class AlcoholModel2Suite extends munit.FunSuite:
  private val male   = Anthropometrics(sex = 1, weightKg = 80, heightM = 1.80, ageYears = 30)
  private val female = Anthropometrics(sex = 0, weightKg = 65, heightM = 1.68, ageYears = 30)

  test("volumes follow Vauthey, Nadler and Watson formulas (values from an independent Python calculation)") {
    val m = Volumes.of(male, Params.published)
    assertEqualsDouble(m.liver, 16.726, 1e-6)
    assertEqualsDouble(m.blood, 53.190608, 1e-6)
    assertEqualsDouble(m.bloodPeripheral, 45.212017, 1e-6)
    assertEqualsDouble(m.bloodCentral, 7.978591, 1e-6)
    assertEqualsDouble(m.plasmaPeripheral, 31.068477, 1e-6)
    assertEqualsDouble(m.totalBodyWater, 545.478571, 1e-6)
    val f = Volumes.of(female, Params.published)
    assertEqualsDouble(f.blood, 40.219952, 1e-6)
    assertEqualsDouble(f.totalBodyWater, 379.657143, 1e-6)
  }

  test("initial state is the model's resting steady state") {
    val y = State.initial(Params.published)
    assertEquals(y.length, State.Size)
    assertEqualsDouble(y(State.VolStomach), 0.001, 1e-15)
    assertEqualsDouble(y(State.Vasopressin), 580.926829, 1e-6)
    assertEqualsDouble(y(State.UrineVolume), 0.05, 1e-15)
    val dy  = new Array[Double](State.Size)
    val rhs = AlcoholModel2.Rhs(Params.published, Volumes.of(male, Params.published))
    rhs(0.0, y, dy)
    for i <- 0 until State.Size if i != State.UrineVolume && i != State.TimeElapsed do
      assertEqualsDouble(dy(i), 0.0, 1e-12, clue = s"state $i")
    assertEqualsDouble(dy(State.UrineVolume), 0.01, 1e-15) // basal urine production
    assertEqualsDouble(dy(State.TimeElapsed), 1.0, 1e-15)
  }

  test("drinking 5% beer at 0.025 L/min into an empty stomach") {
    val y   = State.initial(Params.published)
    val dy  = new Array[Double](State.Size)
    val rhs = AlcoholModel2.Rhs(Params.published, Volumes.of(male, Params.published))
    rhs.inputs = Inputs(etohConcPercent = 5, volRateLPerMin = 0.025, kcalPerLitre = 430)
    rhs(0.0, y, dy)
    assertEqualsDouble(dy(State.VolStomach), 0.025, 1e-12)
    assertEqualsDouble(dy(State.KcalLiquid), 10.75, 1e-12)
    assertEqualsDouble(dy(State.KcalRemain), 10.75, 1e-12)
    assertEqualsDouble(dy(State.StomachConc), 5 * 7891.0 * 25.0, 1e-6)
  }

  test("BrAC formula matches the model's feature definition") {
    assertEqualsDouble(AlcoholModel2.bracMgL(0), 10.0 / 2.1 * 0.00367, 1e-12)
    assertEqualsDouble(AlcoholModel2.bracMgL(100), 10.0 / 2.1 * (0.084 + 0.00367), 1e-12)
  }
