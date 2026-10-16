package owlhours.app.logic

import java.time.Duration
import owlhours.domain.*

class DisplaySuite extends munit.FunSuite:
  test("grid labels speak the chosen unit (breath figures stay blood ‰ on the chart)") {
    assertEquals(Display.gridLabel(1, BacUnit.Permille), "1‰")
    assertEquals(Display.gridLabel(2, BacUnit.PercentBac), "0.2%")
    assertEquals(Display.gridLabel(1, BacUnit.MgPer100ml), "100 mg")
    assertEquals(Display.gridLabel(1, BacUnit.BreathMgPerL), "1‰")
  }

  test("durations read as hours and minutes") {
    assertEquals(Display.duration(Duration.ofMinutes(190)), "3 h 10 min")
    assertEquals(Display.duration(Duration.ofMinutes(45)), "45 min")
    assertEquals(Display.duration(Duration.ofMinutes(120)), "2 h")
  }

  test("a night card line: peak, drinks, standard drinks, duration") {
    val s = Session(SessionId("n"), java.time.Instant.EPOCH, Vector.empty)
    val n = NightSummary(s, Some(owlhours.model.Peak(java.time.Instant.EPOCH, 47.25)), 3, 2.64, Duration.ofMinutes(190), Nil)
    assertEquals(Display.night(n, BacUnit.Permille), "Peak 0.45‰ · 3 drinks · 2.6 standard drinks · 3 h 10 min")
    assertEquals(Display.night(n.copy(peak = None, drinks = 1, standardDrinks = 1.0), BacUnit.Permille),
      "Peak ~0 · 1 drink · 1.0 standard drink · 3 h 10 min")
  }
