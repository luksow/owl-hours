package owlhours.catalogue

import owlhours.domain.*

class EntrySuite extends munit.FunSuite {
  test("ethanol energy uses 7.0 kcal/g, matching EU spirit labels (40 % → 221 kcal/100 ml)") {
    assertEqualsDouble(Energy.ethanolKcalPerLitre(40), 2209.2, 1e-9)
  }

  test("style kcal = ethanol + carbs × 4 (pale lager 5 %, 32 g/L → 40.4 kcal/100 ml)") {
    val lager = Style(CatalogueId("pale-lager"), "Pale lager", Category.Beer, Abv(5), Abv(4.2), Abv(6),
      CarbsPerLitre(32), List(Serving("bottle", Millilitres(330))), Nil, "test", estimate = false)
    assertEqualsDouble(lager.kcalPerLitre.unwrap, 404.15, 1e-9)
    assertEqualsDouble(lager.kcalPerLitreAt(Abv(6)).unwrap, 6 * 10 * 0.789 * 7.0 + 128, 1e-9)
    assertEquals(lager.defaultAbv, Abv(5))
  }

  test("changing a product's ABV keeps its non-alcohol calories") {
    // vodka 40 % at 2210 kcal/L has 0.8 kcal/L of non-alcohol energy
    assertEqualsDouble(Energy.withAbv(2210, 40, 37.5), 0.8 + 2071.125, 1e-9)
    // never negative residual, even for a label that rounds below the ethanol floor
    assertEqualsDouble(Energy.withAbv(2200, 40, 37.5), 2071.125, 1e-9)
  }

  test("categories map to browsing groups") {
    assertEquals(Category.Cider.group, Group.BeerAndCider)
    assertEquals(Category.Sake.group, Group.Wine)
    assertEquals(Category.Vermouth.group, Group.Liqueurs)
    assertEquals(Category.Syrup.group, Group.Mixers)
    assertEquals(Category.Cocktail.group, Group.Cocktails)
    assertEquals(Group.values.map(_.label).toList,
      List("Beer & cider", "Wine", "Cocktails", "Spirits", "Liqueurs & aperitifs", "Mixers & soft"))
  }

  test("mixing methods carry the spec's dilution") {
    assertEquals(Method.values.map(m => m -> m.dilution).toList,
      List(Method.Neat -> 0.0, Method.Built -> 0.10, Method.Stirred -> 0.22, Method.Shaken -> 0.28, Method.Blended -> 0.28))
  }

  test("servings display their volume, with the label when there is one") {
    assertEquals(Serving("", Millilitres(330)).display, "330 ml")
    assertEquals(Serving("  ", Millilitres(330)).display, "330 ml")
    assertEquals(Serving("UK pint", Millilitres(568)).display, "UK pint · 568 ml")
    assertEquals(Serving("ochoko", Millilitres(45.5)).display, "ochoko · 45.5 ml")
  }
}

