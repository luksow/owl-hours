package owlhours.app.logic

import java.time.{Duration, Instant, LocalDate}
import owlhours.domain.*
import owlhours.model.{Peak, Trend}

class DisplaySuite extends munit.FunSuite:
  private val profile = Profile(Sex.Female, LocalDate.of(1998, 1, 1), BodyWeight(62), Height(165))
  private val t0      = Instant.parse("2026-10-14T20:00:00Z")
  private def at(min: Long) = t0.plusSeconds(min * 60)
  private val wine    = DrinkSnapshot(None, "Red wine", Millilitres(175), Abv(13.5), KcalPerLitre(860), Nil)
  private val shot    = DrinkSnapshot(None, "Vodka", Millilitres(40), Abv(40), KcalPerLitre(2210), Nil)
  private val heavy   = Session(SessionId("h"), at(0), Vector.tabulate(16)(k => Intake.Drink(shot, at(k * 8L), Minutes(2))))
  private val hhmm    = (t: Instant) => t.toString.substring(11, 16)

  test("grid labels at half-‰ steps speak the chosen unit (breath figures stay blood ‰ on the chart)") {
    assertEquals(Display.gridLabel(0.5, BacUnit.Permille), "0.5‰")
    assertEquals(Display.gridLabel(1.0, BacUnit.Permille), "1‰")
    assertEquals(Display.gridLabel(0.5, BacUnit.PercentBac), "0.05%")
    assertEquals(Display.gridLabel(2.0, BacUnit.PercentBac), "0.2%")
    assertEquals(Display.gridLabel(1.5, BacUnit.MgPer100ml), "150 mg")
    assertEquals(Display.gridLabel(1.0, BacUnit.BreathMgPerL), "1‰")
    assertEquals(Display.gridLabel(0.0, BacUnit.Permille), "0")
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

  test("an intake reads as a short line") {
    val at = java.time.Instant.EPOCH
    assertEquals(Display.intake(Intake.Water(Millilitres(250), at, Minutes(5))), "Water · 250 ml · 5 min")
    assertEquals(Display.intake(Intake.Meal(Kcal(700), at)), "Food · 700 kcal")
  }

  test("back-to chip: a time when there is one, 'after …+' when capped, nothing once back to ~0") {
    val two = Session(SessionId("a"), at(0), Vector(Intake.Drink(wine, at(0), Minutes(20)), Intake.Drink(wine, at(30), Minutes(20))))
    val t = Tonight.of(profile, Some(two), at(40))
    val noDay = (_: Instant) => "?"
    assertEquals(Display.backTo(t, at(40), hhmm, noDay), Some(s"Back to ~0 by ${hhmm(t.summary.backToZeroBy.get)}"))
    val capped = Tonight.at(Tonight.project(profile, Some(heavy), at(130), horizons = List(1)), at(130))
    assertEquals(Display.backTo(capped, at(130), hhmm, noDay), Some(s"Back to ~0 after ${hhmm(capped.viewTo)}+"))
    assertEquals(Display.backTo(Tonight.of(profile, Some(two), at(24 * 60)), at(24 * 60), hhmm, noDay), None)
  }

  test("trend line: the peak time while it is ahead") {
    val pk = Some(Peak(at(90), 80.0))
    assertEquals(Display.trendLine(Trend.Rising, pk, at(60), hhmm), s"↗ Rising · peak ${hhmm(at(90))}")
    assertEquals(Display.trendLine(Trend.Rising, pk, at(95), hhmm), "↗ Rising")
    assertEquals(Display.trendLine(Trend.Falling, pk, at(120), hhmm), "↘ Falling")
    assertEquals(Display.trendLine(Trend.Steady, None, at(0), hhmm), "→ Steady")
  }

  test("the big number and its unit are split, so the unit can be set smaller") {
    assertEquals(Display.bacParts(0.62 * BacUnit.MgDlPerPermille, 0.0, BacUnit.Permille), ("0.62", "‰"))
    assertEquals(Display.bacParts(0.0, 0.0, BacUnit.Permille), ("0.00", "‰"))
    val (n, u) = Display.bacParts(62.0, 0.0, BacUnit.PercentBac)
    assertEquals(n + u, Display.bac(62.0, 0.0, BacUnit.PercentBac))
  }

  test("log lines show the poured volume; the melt note names the method") {
    val s = DrinkSnapshot(None, "Negroni", Millilitres(99), Abv(24.5), KcalPerLitre(1839.4), Nil, Some(Millilitres(9)))
    assert(Display.intake(Intake.Drink(s, at(0), Minutes(20))).contains("Negroni · 90 ml"))
    assertEquals(Display.meltNote(s, "built over ice"), Some("+ 9 ml melt water (built over ice), included in the strength"))
    assertEquals(Display.meltNote(s.copy(meltWater = None), "built over ice"), None)
  }

  test("final review: grid lines read round numbers in every unit, at their true positions") {
    val k = BacUnit.MgDlPerPermille
    assertEquals(Display.gridLines(BacUnit.Permille, 1.3 * k), List(0.0 -> "0", 0.5 * k -> "0.5‰", 1.0 * k -> "1‰"))
    assertEquals(Display.gridLines(BacUnit.MgPer100ml, 120.0), List(0.0 -> "0", 50.0 -> "50 mg", 100.0 -> "100 mg"))
    assertEquals(Display.gridLines(BacUnit.PercentBac, 120.0), List(0.0 -> "0", 50.0 -> "0.05%", 100.0 -> "0.10%"))
    assertEquals(Display.gridLines(BacUnit.MgPer100ml, 400.0).map(_._2), List("0", "100 mg", "200 mg", "300 mg", "400 mg"))
    assertEquals(Display.gridLines(BacUnit.BreathMgPerL, 1.3 * k).map(_._2), List("0", "0.5‰", "1‰"))
  }

  test("final review: a back-to time more than 16 hours away names its day") {
    val two = Session(SessionId("a"), at(0), Vector(Intake.Drink(wine, at(0), Minutes(20))))
    val t   = Tonight.of(profile, Some(two), at(10))
    val far = t.copy(summary = t.summary.copy(backToZeroBy = Some(at(10 + 27 * 60))))
    val day = (i: Instant) => "Sun"
    assertEquals(Display.backTo(t, at(10), hhmm, day), Some(s"Back to ~0 by ${hhmm(t.summary.backToZeroBy.get)}"))
    assertEquals(Display.backTo(far, at(10), hhmm, day), Some(s"Back to ~0 by Sun ${hhmm(at(10 + 27 * 60))}"))
    val capped = far.copy(summary = far.summary.copy(backToZeroBy = None), capped = true, viewTo = at(10 + 30 * 60))
    assertEquals(Display.backTo(capped, at(10), hhmm, day), Some(s"Back to ~0 after Sun ${hhmm(at(10 + 30 * 60))}+"))
  }
