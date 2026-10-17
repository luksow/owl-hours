package owlhours.catalogue

import owlhours.domain.*
import Fixtures.*

class ResolveSuite extends munit.FunSuite {
  private def ok(c: Choice): DrinkSnapshot = Resolve(c).fold(e => fail(e), identity)

  test("style at a chosen ABV and size") {
    val s = ok(Choice.OfStyle(lager, Abv(5.5), Millilitres(500)))
    assertEquals(s.catalogueId, Some(CatalogueId("pale-lager")))
    assertEquals(s.name, "Pale lager")
    assertEquals(s.volume, Millilitres(500))
    assertEquals(s.abv, Abv(5.5))
    assertEqualsDouble(s.kcalPerLitre.unwrap, 5.5 * 10 * 0.789 * 7.0 + 128, 1e-9)
    assertEquals(s.components, Nil)
  }

  test("style ABV outside its range is rejected with a readable message") {
    assertEquals(
      Resolve(Choice.OfStyle(lager, Abv(7), Millilitres(500))),
      Left("ABV for Pale lager must be between 4.2 and 6 %, got 7")
    )
  }

  test("product with an ABV override keeps its non-alcohol calories") {
    val s = ok(Choice.OfProduct(gin, Millilitres(40), Some(Abv(37.5))))
    assertEquals(s.abv, Abv(37.5))
    assertEqualsDouble(s.kcalPerLitre.unwrap, Energy.withAbv(2210, 40, 37.5), 1e-9)
  }

  test("ABV override on a fixed product is rejected") {
    assertEquals(Resolve(Choice.OfProduct(campari, Millilitres(30), Some(Abv(20)))), Left("Campari has a fixed ABV"))
  }

  test("Negroni 30/30/30 built over ice: 10 % ice melt") {
    // ethanol 12 + 7.5 + 4.8 = 24.3 ml; liquid 90 ml; water 9 ml; total 99 ml
    // kcal 30 × (2.21 + 2.36 + 1.50) = 182.1
    val s = ok(Choice.OfRecipe(negroni))
    assertEquals(s.name, "Negroni")
    assertEqualsDouble(s.volume.unwrap, 99, 1e-9)
    assertEqualsDouble(s.abv.unwrap, 24.3 / 99 * 100, 1e-9)
    assertEqualsDouble(s.kcalPerLitre.unwrap, 182.1 / 99 * 1000, 1e-9)
    assertEquals(
      s.components.map(c => (c.name, c.volume.unwrap)),
      List(("Gin", 30.0), ("Campari", 30.0), ("Sweet vermouth", 30.0))
    )
  }

  test("heavy hand ×1.5 scales every ingredient and keeps the ABV") {
    val s = ok(Choice.OfRecipe(negroni, strength = 1.5))
    assertEquals(s.name, "Negroni (×1.5)")
    assertEqualsDouble(s.volume.unwrap, 148.5, 1e-9)
    assertEqualsDouble(s.abv.unwrap, 24.3 / 99 * 100, 1e-9)
  }

  test("custom amounts per ingredient") {
    val s = ok(Choice.OfRecipe(negroni, volumes = Some(List(Millilitres(50), Millilitres(50), Millilitres(50)))))
    assertEquals(s.name, "Negroni (custom)")
    assertEqualsDouble(s.volume.unwrap, 165, 1e-9) // 150 + 10 % ice melt
  }

  test("ingredients added after mixing get no ice melt") {
    // shaken part 30 + 15 = 45 ml × 1.28 = 57.6 ml, plus 60 ml champagne
    val s = ok(Choice.OfRecipe(toppedUp))
    assertEqualsDouble(s.volume.unwrap, 117.6, 1e-9)
    assertEqualsDouble(s.abv.unwrap, (12 + 7.2) / 117.6 * 100, 1e-9)
  }

  test("dilution can be switched off") {
    val s = ok(Choice.OfRecipe(negroni, dilution = false))
    assertEqualsDouble(s.volume.unwrap, 90, 1e-9)
    assertEqualsDouble(s.abv.unwrap, 27.0, 1e-9)
  }

  test("solids (sugar) add calories but no volume, scaled with strength") {
    // rum 60 × 2.08 + lime 20 × 0.26 = 124.8 + 5.2 = 130 kcal, + 40 sugar = 170 kcal; 80 ml + 28 % = 102.4 ml
    val s = ok(Choice.OfRecipe(daiquiri))
    assertEqualsDouble(s.volume.unwrap, 102.4, 1e-9)
    assertEqualsDouble(s.kcalPerLitre.unwrap, 170 / 102.4 * 1000, 1e-9)
    val strong = ok(Choice.OfRecipe(daiquiri, strength = 2))
    assertEqualsDouble(strong.kcalPerLitre.unwrap * strong.volume.unwrap / 1000, 340, 1e-9)
  }

  test("strength and custom amounts are validated") {
    assertEquals(
      Resolve(Choice.OfRecipe(negroni, strength = 2.5)),
      Left("Strength must be between ×0.75 and ×2, got ×2.5")
    )
    assertEquals(
      Resolve(Choice.OfRecipe(negroni, strength = 0.5)),
      Left("Strength must be between ×0.75 and ×2, got ×0.5")
    )
    assertEquals(
      Resolve(Choice.OfRecipe(negroni, volumes = Some(List(Millilitres(30), Millilitres(30))))),
      Left("Negroni needs 3 amounts, got 2")
    )
  }

  test("review: extreme custom amounts return a Left instead of throwing") {
    val huge = Resolve(Choice.OfRecipe(negroni, volumes = Some(List.fill(3)(Millilitres(2000)))))
    assertEquals(huge, Left("Negroni: Volume must be above 0 and at most 5000 ml, got 6600"))
    val tiny = Resolve(Choice.OfRecipe(daiquiri, volumes = Some(List(Millilitres(1), Millilitres(1)))))
    assert(tiny.left.exists(_.startsWith("Daiquiri: Energy density must be between 0 and 10000 kcal/L")), tiny.toString)
  }

  test("review: a NaN strength is rejected, not passed through") {
    assertEquals(
      Resolve(Choice.OfRecipe(negroni, strength = Double.NaN)),
      Left("Strength must be between ×0.75 and ×2, got ×NaN")
    )
  }

  test("a built Negroni pours 90 ml; 9 ml of melt water is added for the model, not to the alcohol") {
    val s = ok(Choice.OfRecipe(negroni))
    assertEqualsDouble(s.poured.unwrap, 90.0, 1e-9)
    assertEqualsDouble(s.meltWater.get.unwrap, 9.0, 1e-9)
    assertEqualsDouble(s.volume.unwrap, 99.0, 1e-9)
    val undiluted = ok(Choice.OfRecipe(negroni, dilution = false))
    assertEqualsDouble(s.ethanolGrams, undiluted.ethanolGrams, 1e-9)
    assertEquals(undiluted.meltWater, None)
    assertEquals(undiluted.poured, undiluted.volume)
  }
}
