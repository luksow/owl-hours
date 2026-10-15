package owlhours.app.logic

import java.time.{Instant, LocalDate}
import owlhours.domain.*

class WhatIfSuite extends munit.FunSuite:
  private val profile = Profile(Sex.Male, LocalDate.of(1996, 1, 1), BodyWeight(80), Height(180))
  private val t0      = Instant.parse("2026-10-14T20:00:00Z")
  private def at(min: Long) = t0.plusSeconds(min * 60)
  private val pint    = DrinkSnapshot(None, "Pale lager", Millilitres(568), Abv(5), KcalPerLitre(404), Nil)
  private def drink(min: Long) = Intake.Drink(pint, at(min), Minutes(30))
  private val label   = (t: Instant) => t.toString.substring(11, 16)

  test("the first drink of the night: no before, a peak after, no ghost") {
    val w = WhatIf.of(profile, Vector.empty, drink(0), at(0))
    assertEquals((w.peakBefore, w.ghost), (None, None))
    assert(w.peakAfter.exists(_.bacMgDl > 0))
    assertEquals(w.markers.size, 1)
  }

  private val twoPints = List(drink(0), drink(20)).foldLeft(Vector.empty[Session])(Sessions.add(profile, _, _))

  test("another pint raises the peak and pushes back-to-~0 later; the ghost is the night without it") {
    val w = WhatIf.of(profile, twoPints, drink(40), at(40))
    assert(w.peakAfter.get.bacMgDl > w.peakBefore.get.bacMgDl)
    assert(w.zeroAfter.get.isAfter(w.zeroBefore.get))
    assertEquals(w.ghost.map(_.samples.size), Some(w.tonight.projection.curve.samples.size))
    assertEquals(w.markers.size, 3)
  }

  test("a drink logged in the past joins the current night") {
    val night = Sessions.add(profile, Vector.empty, drink(0))
    assert(WhatIf.of(profile, night, drink(-20), at(30)).peakBefore.isDefined)
  }

  test("the description is numbers and times, never reassurance") {
    val w     = WhatIf.of(profile, twoPints, drink(40), at(40))
    val lines = WhatIf.describe(w, BacUnit.Permille, label)
    assertEquals(lines.size, 2)
    assert(lines.head.matches("""Peak \d\.\d\d‰ → \d\.\d\d‰ at \d\d:\d\d"""), lines.head)
    assert(lines(1).matches("""Back to ~0 by \d\d:\d\d → \d\d:\d\d"""), lines(1))
    for l <- lines do assert(!l.toLowerCase.matches(".*(safe|fine|okay|one more|you can).*"), l)
    val first = WhatIf.describe(WhatIf.of(profile, Vector.empty, drink(0), at(0)), BacUnit.Permille, label)
    assert(first.head.matches("""Peak \d\.\d\d‰ at \d\d:\d\d"""), first.head)
  }
