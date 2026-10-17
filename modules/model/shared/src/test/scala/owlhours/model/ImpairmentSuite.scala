package owlhours.model

import Impairment.*

class ImpairmentSuite extends munit.FunSuite {
  test("thresholds in per mille, lower bound inclusive") {
    val cases = List(
      0.0 -> Sober, 0.19 -> Sober, 0.2 -> WarmingUp, 0.49 -> WarmingUp, 0.5 -> Tipsy, 0.8 -> Buzzed,
      1.2 -> Drunk, 1.99 -> Drunk, 2.0 -> Wobbly, 3.0 -> Wobbly, 3.01 -> TakeCare, 5.0 -> TakeCare
    )
    for (p, expected) <- cases do assertEquals(Impairment.fromPermille(p), expected, clue = s"$p ‰")
  }

  test("mg/dL uses the model's 105 conversion") {
    assertEquals(Impairment.fromMgDl(52.5), Tipsy)   // 0.5 ‰
    assertEquals(Impairment.fromMgDl(315.1), TakeCare)
  }
}
