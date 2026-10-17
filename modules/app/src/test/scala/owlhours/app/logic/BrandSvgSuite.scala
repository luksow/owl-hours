package owlhours.app.logic

class BrandSvgSuite extends munit.FunSuite:
  private def count(s: String, sub: String) = s.sliding(sub.length).count(_ == sub)

  test("the lit lockup is the owl-head mark, the script name and the short tagline, glowing through its own filter") {
    val svg = BrandSvg.wordmark(lit = true)
    assert(svg.contains("""aria-label="Owl Hours""""), svg)
    assert(svg.contains(">Owl Hours</text>"))
    assert(svg.contains(">KNOW YOUR CURVE</text>"))
    assert(svg.contains(s"""d="${BrandSvg.Head}""""))
    assert(svg.contains("""<filter id="oh-neon-wordmark""""))
    assert(svg.contains("url(#oh-neon-wordmark)"))
    assert(svg.contains("#FF3F8E") && svg.contains("#FFD2E3"))
  }

  test("the unlit lockup has no glow and uses only the dark tube colours") {
    val svg = BrandSvg.wordmark(lit = false)
    assert(!svg.contains("<filter"), svg)
    assert(!svg.contains("#FF3F8E") && !svg.contains("#5CE1FF") && !svg.contains("#FFB03B"))
    assert(svg.contains("#7A1F45") && svg.contains("#0E6E86") && svg.contains("#A85A0C"))
  }

  test("only Hydration hero and Slow sipper have plaques (§8.6); each has its own filter id and is decorative") {
    val water = BrandSvg.badge(Badge.HydrationHero).get
    val snail = BrandSvg.badge(Badge.SlowSipper).get
    assertEquals(BrandSvg.badge(Badge.FedAndSteady), None)
    assertEquals(BrandSvg.badge(Badge.EarlyBird), None)
    assert(water.contains("""<filter id="oh-neon-hydration""""), water)
    assert(snail.contains("""<filter id="oh-neon-slow""""), snail)
    List(water, snail).foreach { s =>
      assert(s.contains("""aria-hidden="true""""))
      assertEquals(count(s, "<filter "), 1)
    }
    assert(water.contains("#5CE1FF") && !water.contains("#FFB03B"))
    assert(snail.contains("#FFB03B") && !snail.contains("#5CE1FF"))
  }

  test("the mark's head stays inside its 100-unit box") {
    val numbers = "-?\\d+(\\.\\d+)?".r.findAllIn(BrandSvg.Head).map(_.toDouble).toList
    assert(numbers.nonEmpty)
    assert(numbers.forall(v => v >= 0 && v <= 100), numbers)
  }
