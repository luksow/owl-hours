package owlhours.app.logic

import owlhours.domain.BacUnit

class DisplaySuite extends munit.FunSuite:
  test("grid labels speak the chosen unit (breath figures stay blood ‰ on the chart)") {
    assertEquals(Display.gridLabel(1, BacUnit.Permille), "1‰")
    assertEquals(Display.gridLabel(2, BacUnit.PercentBac), "0.2%")
    assertEquals(Display.gridLabel(1, BacUnit.MgPer100ml), "100 mg")
    assertEquals(Display.gridLabel(1, BacUnit.BreathMgPerL), "1‰")
  }
