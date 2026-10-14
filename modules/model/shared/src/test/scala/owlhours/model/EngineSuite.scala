package owlhours.model

class EngineSuite extends munit.FunSuite:
  private val male = Anthropometrics(1, 80, 1.80, 30)
  private val beer = DrinkInput(startMin = 10, durationMin = 20, volumeL = 0.5, abvPercent = 5, kcalPerLitre = 430)

  test("overlapping drinks are mixed by volume rate") {
    val water = DrinkInput(20, 10, 0.25, 0, 0)
    val s     = Schedule(Seq(beer, water), Nil)
    val mix   = s.inputsAt(25)
    // beer 0.025 L/min, water 0.025 L/min
    assertEqualsDouble(mix.volRateLPerMin, 0.05, 1e-12)
    assertEqualsDouble(mix.etohConcPercent, 2.5, 1e-12)
    assertEqualsDouble(mix.kcalPerLitre, 215, 1e-12)
    assertEquals(s.inputsAt(5), Inputs.zero)
    assertEquals(s.inputsAt(15), Inputs(5, 0.025, 430)) // beer alone, before the water starts
    assertEquals(s.inputsAt(30), Inputs.zero)            // both have ended (intervals are half-open)
    assertEquals(s.segments(0, 60).map(seg => (seg.start, seg.end)), Vector((0.0, 10.0), (10.0, 20.0), (20.0, 30.0), (30.0, 60.0)))
  }

  test("meal times become segment boundaries carrying the meal") {
    val s = Schedule(Nil, Seq(MealInput(15, 600), MealInput(15, 100)))
    val segs = s.segments(0, 60)
    assertEquals(segs.map(_.start), Vector(0.0, 15.0))
    assertEquals(segs(1).mealKcal, 700.0)
  }

  test("no intakes keeps blood alcohol at zero") {
    val r = Engine.run(male, Schedule(Nil, Nil), 0, 120, 5)
    assertEquals(r.size, 25)
    assert((0 until r.size).forall(i => r.bacMgDl(i) == 0.0))
  }

  test("a beer raises then lowers blood alcohol") {
    val r    = Engine.run(male, Schedule(Seq(beer), Nil), 0, 480, 5)
    val bac  = (0 until r.size).map(r.bacMgDl)
    val peak = bac.indexOf(bac.max)
    assert(bac.max > 5, s"peak ${bac.max}")
    assert(peak > 2 && peak < 30, s"peak index $peak")
    assert(bac.last < 0.5)
  }

  test("new drink event re-bases liquid kcal on the undrained remainder (values from SUND)") {
    val second = beer.copy(startMin = 60)
    val r      = Engine.run(male, Schedule(Seq(beer, second), Nil), 0, 61, 1)
    // reference: sund 2.7.0, same schedule, states read at t = 59 and t = 61
    assertEqualsDouble(r.states(59)(State.KcalLiquid), 214.997, 0.5)
    assertEqualsDouble(r.states(59)(State.KcalRemain), 37.070, 0.5)
    assertEqualsDouble(r.states(61)(State.KcalLiquid), 46.207, 0.5)
    assertEqualsDouble(r.states(61)(State.KcalRemain), 41.988, 0.5)
  }

  test("food_digested event zeroes solid food and stops the food bonus") {
    val r     = Engine.run(male, Schedule(Nil, Seq(MealInput(5, 300))), 0, 1200, 5)
    val first = r.states.indexWhere(s => s(State.MaxKcalSolid) == 0.0 && s(State.TimeElapsed) > 5)
    assert(first > 0, "food was never marked as digested")
    val s = r.states(first)
    assertEquals(s(State.KcalSolid), 0.0)
    assert(r.states.drop(first).forall(_(State.KcalSolid) == 0.0))
  }
