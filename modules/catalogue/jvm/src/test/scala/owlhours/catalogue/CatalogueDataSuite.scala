package owlhours.catalogue

import owlhours.domain.*

/** Gate on the real catalogue.json: anything that fails here must not be deployed. */
class CatalogueDataSuite extends munit.FunSuite {
  private def c = TestData.catalogue

  test("the shipped file loads without errors") {
    assertEquals(c.schemaVersion, Catalogue.SchemaVersion)
  }

  test("v1 scope: enough entries in every part of the catalogue") {
    assert(c.styles.size >= 60, s"styles: ${c.styles.size}")
    assert(c.products.count(_.category.group != Group.Mixers) >= 80)
    assert(c.products.count(_.category.group == Group.Mixers) >= 30)
    assert(c.recipes.size >= 60, s"recipes: ${c.recipes.size}")
  }

  test("every entry has a name and a source; every style and product has at least one serving") {
    for e <- c.all do {
      assert(e.name.trim.nonEmpty, e.id.unwrap)
      assert(e.source.trim.nonEmpty || e.id.unwrap == "water", s"${e.id.unwrap} has no source")
    }
    for i <- c.styles ++ c.products do assert(i.servings.nonEmpty, s"${i.id.unwrap} has no serving")
  }

  test("every cocktail resolves at its standard serve to a plausible drink") {
    for r <- c.recipes do {
      val s = Resolve(Choice.OfRecipe(r)).fold(e => fail(s"${r.id.unwrap}: $e"), identity)
      assert(s.abv.unwrap > 0 && s.abv.unwrap <= 40, s"${r.id.unwrap}: ${s.abv.unwrap} %")
      assert(s.volume.unwrap >= 40 && s.volume.unwrap <= 300, s"${r.id.unwrap}: ${s.volume.unwrap} ml")
    }
  }

  test("known values from the sources") {
    val lager = c.get(CatalogueId("pale-lager")).collect { case s: Style => s }.get
    assertEqualsDouble(lager.kcalPerLitre.unwrap / 10, 40.4, 0.1) // USDA regular beer ≈ 43 kcal/100 g at ~4.6 %
    val vodka = c.get(CatalogueId("vodka")).collect { case p: Product => p }.get
    assertEquals(vodka.kcalPerLitre, KcalPerLitre(2210))           // Absolut label 221 kcal/100 ml
    assert(vodka.abvAdjustable)
    val negroni = c.get(CatalogueId("negroni")).collect { case r: Recipe => r }.get
    assertEqualsDouble(Resolve(Choice.OfRecipe(negroni)).toOption.get.abv.unwrap, 24.3 / 99 * 100, 1e-9)
    def volumeOf(id: String) =
      Resolve(Choice.OfRecipe(c.get(CatalogueId(id)).collect { case r: Recipe => r }.get)).toOption.get.volume.unwrap
    assertEqualsDouble(volumeOf("irish-coffee"), 220, 1e-9)       // hot, no ice: no dilution
    assertEqualsDouble(volumeOf("french-75"), 60 * 1.28 + 60, 1e-9) // champagne added after shaking
  }

  test("drinks served as shots or neat have a measured serving, not just a dash") {
    val fernet = c.get(CatalogueId("fernet-branca")).collect { case p: Product => p }.get
    assert(fernet.servings.exists(_.volume.unwrap >= 25), fernet.servings.toString)
    val dashOnly = Set("angostura", "peychauds", "orange-bitters", "worcestershire")
    for p <- c.products if !dashOnly(p.id.unwrap) do
      assert(p.servings.exists(_.volume.unwrap >= 10), s"${p.id.unwrap} has only tiny servings: ${p.servings}")
  }

  test("browsing groups partition the catalogue") {
    assertEquals(Group.values.toList.map(c.inGroup(_).size).sum, c.all.size)
    assert(c.inGroup(Group.Cocktails).forall(_.isInstanceOf[Recipe]))
  }

  test("no style or product lists the same serving volume twice") {
    for i <- c.styles ++ c.products do {
      val volumes = i.servings.map(_.volume.unwrap)
      assertEquals(volumes.distinct.size, volumes.size, s"${i.id.unwrap}: ${i.servings}")
    }
  }


  test("every beer, cider and seltzer offers 330, 500 and 568 ml") {
    for s <- c.styles if Set(Category.Beer, Category.Cider, Category.Seltzer)(s.category) do {
      val volumes = s.servings.map(_.volume.unwrap).toSet
      assert(Set(330.0, 500.0, 568.0).subsetOf(volumes), s"${s.id.unwrap}: ${s.servings.map(_.display)}")
    }
  }

  test("every serving has a label and servings are sorted by volume") {
    for i <- c.styles ++ c.products do {
      assert(i.servings.forall(_.label.trim.nonEmpty), s"${i.id.unwrap}: ${i.servings}")
      val volumes = i.servings.map(_.volume.unwrap)
      assertEquals(volumes, volumes.sorted, i.id.unwrap)
    }
  }
}
