package owlhours.model

/** Adaptive Dormand–Prince 5(4) integrator with cubic Hermite dense output and event location. */
object Solver {
  trait Rhs {
    def apply(t: Double, y: Array[Double], dy: Array[Double]): Unit
  }

  /** Fires when its value goes from >= 0 to < 0 within a step. */
  type Event = (Double, Array[Double]) => Double

  final case class Stop(t: Double, nextSample: Int, eventFired: Boolean)

  final case class Tolerance(rtol: Double = 1e-8, atol: Double = 1e-10, hInit: Double = 0.01, hMax: Double = 2.0)

  private val (c2, c3, c4, c5) = (1.0 / 5, 3.0 / 10, 4.0 / 5, 8.0 / 9)
  private val a21              = 1.0 / 5
  private val (a31, a32)       = (3.0 / 40, 9.0 / 40)
  private val (a41, a42, a43)  = (44.0 / 45, -56.0 / 15, 32.0 / 9)
  private val (a51, a52, a53, a54) = (19372.0 / 6561, -25360.0 / 2187, 64448.0 / 6561, -212.0 / 729)
  private val (a61, a62, a63, a64, a65) = (9017.0 / 3168, -355.0 / 33, 46732.0 / 5247, 49.0 / 176, -5103.0 / 18656)
  private val (b1, b3, b4, b5, b6) = (35.0 / 384, 500.0 / 1113, 125.0 / 192, -2187.0 / 6784, 11.0 / 84)
  private val (e1, e3, e4, e5, e6, e7) =
    (71.0 / 57600, -71.0 / 16695, 71.0 / 1920, -17253.0 / 339200, 22.0 / 525, -1.0 / 40)

  def integrate(
      f: Rhs,
      t0: Double,
      y: Array[Double],
      t1: Double,
      grid: Array[Double],
      firstSample: Int,
      out: Array[Array[Double]],
      event: Option[Event],
      tol: Tolerance = Tolerance()
  ): Stop = {
    val n                                  = y.length
    val (k1, k2, k3, k4, k5, k6, k7)       = (arr(n), arr(n), arr(n), arr(n), arr(n), arr(n), arr(n))
    val (tmp, yNew, interp)                = (arr(n), arr(n), arr(n))
    var t                                  = t0
    var h                                  = math.min(tol.hInit, t1 - t0)
    var next                               = firstSample
    f(t, y, k1)

    def hermite(theta: Double, hh: Double, into: Array[Double]): Unit = {
      val th2 = theta * theta
      val th3 = th2 * theta
      val h00 = 2 * th3 - 3 * th2 + 1
      val h10 = th3 - 2 * th2 + theta
      val h01 = -2 * th3 + 3 * th2
      val h11 = th3 - th2
      var i   = 0
      while i < n do {
        into(i) = h00 * y(i) + h10 * hh * k1(i) + h01 * yNew(i) + h11 * hh * k7(i)
        i += 1
      }
    }

    def emitUpTo(limit: Double, hh: Double): Unit =
      while next < grid.length && grid(next) <= limit do {
        if grid(next) > t then {
          hermite((grid(next) - t) / hh, hh, interp)
          out(next) = interp.clone()
        }
        next += 1
      }

    while t < t1 do {
      val last = t + h >= t1 || t1 - (t + h) < 1e-12
      if last then h = t1 - t
      stage(tmp, y, h, k1, a21)
      f(t + c2 * h, tmp, k2)
      stage(tmp, y, h, k1, a31, k2, a32)
      f(t + c3 * h, tmp, k3)
      stage(tmp, y, h, k1, a41, k2, a42, k3, a43)
      f(t + c4 * h, tmp, k4)
      stage(tmp, y, h, k1, a51, k2, a52, k3, a53, k4, a54)
      f(t + c5 * h, tmp, k5)
      stage(tmp, y, h, k1, a61, k2, a62, k3, a63, k4, a64, k5, a65)
      f(t + h, tmp, k6)
      stage(yNew, y, h, k1, b1, k3, b3, k4, b4, k5, b5, k6, b6)
      val tNew = if last then t1 else t + h
      f(tNew, yNew, k7)

      var errSq  = 0.0
      var finite = true
      var i      = 0
      while i < n do {
        val ei    = h * (e1 * k1(i) + e3 * k3(i) + e4 * k4(i) + e5 * k5(i) + e6 * k6(i) + e7 * k7(i))
        val scale = tol.atol + tol.rtol * math.max(math.abs(y(i)), math.abs(yNew(i)))
        if ei.isNaN || ei.isInfinite || yNew(i).isNaN || yNew(i).isInfinite then finite = false
        errSq += (ei / scale) * (ei / scale)
        i += 1
      }
      val err = if finite then math.sqrt(errSq / n) else Double.PositiveInfinity

      if err <= 1.0 then {
        val hit = event.exists(g => g(t, y) >= 0 && g(tNew, yNew) < 0)
        if hit then {
          val g      = event.get
          var lo     = 0.0
          var hi     = 1.0
          var iter   = 0
          while iter < 60 do {
            val mid = 0.5 * (lo + hi)
            hermite(mid, h, interp)
            if g(t + mid * h, interp) >= 0 then lo = mid else hi = mid
            iter += 1
          }
          val tE = t + hi * h
          emitUpTo(tE, h)
          hermite(hi, h, interp)
          System.arraycopy(interp, 0, y, 0, n)
          return Stop(tE, next, eventFired = true)
        }
        emitUpTo(tNew, h)
        System.arraycopy(yNew, 0, y, 0, n)
        System.arraycopy(k7, 0, k1, 0, n)
        t = tNew
      }

      val fac = if err == 0 then 5.0 else math.min(5.0, math.max(0.2, 0.9 * math.pow(err, -0.2)))
      h = math.min(h * fac, tol.hMax)
      // Only a rejected step that shrinks below 1e-10 is a failure; a tiny accepted final step is fine.
      if err > 1.0 && h < 1e-10 then throw IllegalStateException(s"Step size underflow at t=$t (non-finite or stiff state)")
    }
    Stop(t, next, eventFired = false)
  }

  private def arr(n: Int) = new Array[Double](n)

  private def stage(into: Array[Double], y: Array[Double], h: Double, ks: Any*): Unit = {
    var i = 0
    while i < y.length do {
      var acc = 0.0
      var j   = 0
      while j < ks.length do {
        acc += ks(j + 1).asInstanceOf[Double] * ks(j).asInstanceOf[Array[Double]](i)
        j += 2
      }
      into(i) = y(i) + h * acc
      i += 1
    }
  }
}
