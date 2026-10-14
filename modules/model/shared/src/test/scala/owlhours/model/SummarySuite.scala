package owlhours.model

import java.time.Instant

class SummarySuite extends munit.FunSuite:
  private val t0 = Instant.parse("2026-10-14T20:00:00Z")
  private def curve(values: Double*): Curve =
    Curve(values.toVector.zipWithIndex.map((v, i) => BacSample(t0.plusSeconds(i * 300L), v, 0))) // 5-min steps

  test("current, rising trend, peak and back-to-zero") {
    val c = curve(0, 20, 40, 60, 50, 40, 30, 20, 10, 5, 0)
    val s = Summary.of(c, t0.plusSeconds(600)) // at 40 mg/dL, next 10 min goes to 50 → rising
    assertEquals(s.currentMgDl, 40.0)
    assertEquals(s.trend, Trend.Rising)
    assertEquals(s.peak, Some(Peak(t0.plusSeconds(900), 60)))
    assertEquals(s.backToZeroBy, Some(t0.plusSeconds(8 * 300))) // first sample below 10.5 that stays below
  }

  test("falling and steady") {
    val c = curve(60, 50, 40, 40, 40, 40)
    assertEquals(Summary.of(c, t0).trend, Trend.Falling)
    assertEquals(Summary.of(c, t0.plusSeconds(600)).trend, Trend.Steady)
  }

  test("nothing to wait for when already below the threshold, nothing known when still above at the end") {
    assertEquals(Summary.of(curve(0, 0, 0), t0).backToZeroBy, None)
    assertEquals(Summary.of(curve(30, 40, 50), t0).backToZeroBy, None)
    assertEquals(Summary.of(curve(0, 0.5, 0), t0).peak, None) // below 1 mg/dL is not a peak
  }

  test("`now` outside the curve clamps to its ends") {
    val c = curve(10, 20, 30)
    assertEquals(Summary.of(c, t0.minusSeconds(60)).currentMgDl, 10.0)
    assertEquals(Summary.of(c, t0.plusSeconds(99999)).currentMgDl, 30.0)
  }
