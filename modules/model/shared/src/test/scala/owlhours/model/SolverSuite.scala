package owlhours.model

class SolverSuite extends munit.FunSuite:
  private val decay: Solver.Rhs = (_, y, dy) => dy(0) = -0.5 * y(0)

  test("exponential decay matches the exact solution at grid points") {
    val y    = Array(1.0)
    val grid = Array.tabulate(11)(i => i.toDouble)
    val out  = new Array[Array[Double]](11)
    out(0) = y.clone()
    val stop = Solver.integrate(decay, 0.0, y, 10.0, grid, 1, out, None)
    assertEquals(stop.nextSample, 11)
    assert(!stop.eventFired)
    for i <- 0 to 10 do assertEqualsDouble(out(i)(0), math.exp(-0.5 * i), 1e-6, clue = s"t=$i")
  }

  test("harmonic oscillator keeps phase over 10 periods") {
    val osc: Solver.Rhs = (_, y, dy) => { dy(0) = y(1); dy(1) = -y(0) }
    val y    = Array(1.0, 0.0)
    val t1   = 20 * math.Pi
    val stop = Solver.integrate(osc, 0.0, y, t1, Array(0.0), 1, new Array[Array[Double]](1), None)
    assertEqualsDouble(stop.t, t1, 1e-12)
    assertEqualsDouble(y(0), 1.0, 1e-5)
    assertEqualsDouble(y(1), 0.0, 1e-5)
  }

  test("event stops integration where the event function crosses zero") {
    val fall: Solver.Rhs = (_, _, dy) => dy(0) = -1.0 // y = 3 - t
    val y    = Array(3.0)
    val grid = Array(0.0, 1.0, 2.0, 3.0, 4.0)
    val out  = new Array[Array[Double]](5)
    val stop = Solver.integrate(fall, 0.0, y, 4.0, grid, 1, out, Some((_, s) => s(0) - 0.5))
    assert(stop.eventFired)
    assertEqualsDouble(stop.t, 2.5, 1e-9)
    assertEqualsDouble(y(0), 0.5, 1e-9)
    assertEquals(stop.nextSample, 3) // samples at t=1 and t=2 were written, t=3 was not
    assertEqualsDouble(out(2)(0), 1.0, 1e-9)
  }

  test("a non-finite derivative fails loudly instead of returning garbage") {
    val bad: Solver.Rhs = (_, _, dy) => dy(0) = Double.NaN
    intercept[IllegalStateException](Solver.integrate(bad, 0.0, Array(1.0), 1.0, Array(0.0), 1, new Array[Array[Double]](1), None))
  }
