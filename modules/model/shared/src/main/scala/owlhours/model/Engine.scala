package owlhours.model

/** Runs alcohol_model_2 over a schedule, reproducing SUND's event semantics. Times in minutes. */
object Engine:
  final case class Result(times: Array[Double], states: Array[Array[Double]]):
    def size: Int               = times.length
    def bacMgDl(i: Int): Double = states(i)(State.BloodConcPeripheral)
    def bracMgL(i: Int): Double = AlcoholModel2.bracMgL(states(i)(State.BloodConcCentral))

  /** food_digested: fires when Kcal_Solid drops below 0 while a meal is being digested. */
  private val foodDigested: Solver.Event = (_, s) => if s(State.MaxKcalSolid) > 0 then s(State.KcalSolid) else 1.0

  def run(
      anth: Anthropometrics,
      schedule: Schedule,
      from: Double,
      to: Double,
      stepMin: Double,
      params: Params = Params.published
  ): Result =
    require(to > from, s"to ($to) must be after from ($from)")
    require(stepMin > 0, "stepMin must be positive")
    val rhs  = AlcoholModel2.Rhs(params, Volumes.of(anth, params))
    val y    = State.initial(params)
    val n    = math.floor((to - from) / stepMin + 1e-9).toInt + 1
    val grid = Array.tabulate(n)(i => from + i * stepMin)
    val out  = new Array[Array[Double]](n)
    out(0) = y.clone()
    var next     = 1
    var prevRate = 0.0
    for seg <- schedule.segments(from, to) do
      // new_drink: rising edge of the combined drinking rate
      if seg.inputs.volRateLPerMin > 0 && prevRate == 0 then y(State.KcalLiquid) = y(State.KcalRemain)
      // new_meal
      if seg.mealKcal > 0 then
        val total = seg.mealKcal + y(State.KcalSolid)
        y(State.MaxKcalSolid) = total
        y(State.KcalSolid) = total
        y(State.TimeElapsed) = 0
      prevRate = seg.inputs.volRateLPerMin
      rhs.inputs = seg.inputs
      var t = seg.start
      while t < seg.end do
        val stop = Solver.integrate(rhs, t, y, seg.end, grid, next, out, Some(foodDigested))
        next = stop.nextSample
        t = stop.t
        if stop.eventFired then
          y(State.KcalSolid) = 0
          y(State.MaxKcalSolid) = 0
    Result(grid, out)
