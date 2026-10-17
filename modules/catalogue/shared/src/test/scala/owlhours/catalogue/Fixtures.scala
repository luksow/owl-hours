package owlhours.catalogue

import owlhours.domain.*

object Fixtures {
  private def product(id: String, name: String, cat: Category, abv: Double, kcal100: Double, adjustable: Boolean) =
    Product(CatalogueId(id), name, cat, Abv(abv), KcalPerLitre(kcal100 * 10), adjustable,
      List(Serving("shot", Millilitres(40))), Nil, "test", estimate = false)

  val gin      = product("gin", "Gin", Category.Spirit, 40, 221, adjustable = true)
  val campari  = product("campari", "Campari", Category.Aperitif, 25, 236, adjustable = false)
  val vermouth = product("vermouth-sweet", "Sweet vermouth", Category.Vermouth, 16, 150, adjustable = false)
  val lime     = product("lime-juice", "Fresh lime juice", Category.Mixer, 0, 26, adjustable = false)
  val rum      = product("rum-white", "White rum", Category.Spirit, 37.5, 208, adjustable = true)

  val lager = Style(CatalogueId("pale-lager"), "Pale lager", Category.Beer, Abv(5), Abv(4.2), Abv(6),
    CarbsPerLitre(32), List(Serving("bottle", Millilitres(330))), Nil, "test", estimate = false)

  val negroni = Recipe(CatalogueId("negroni"), "Negroni", Method.Built,
    List(RecipeLine(gin, Millilitres(30)), RecipeLine(campari, Millilitres(30)), RecipeLine(vermouth, Millilitres(30))),
    solidsKcal = 0, Nil, "test", estimate = false)

  val fizz = product("champagne-brut", "Champagne", Category.Sparkling, 12, 76, adjustable = false)

  /** French-75-like: shaken base topped with sparkling wine after mixing. */
  val toppedUp = Recipe(CatalogueId("french-75"), "French 75", Method.Shaken,
    List(RecipeLine(gin, Millilitres(30)), RecipeLine(lime, Millilitres(15)), RecipeLine(fizz, Millilitres(60), afterMixing = true)),
    solidsKcal = 0, Nil, "test", estimate = false)

  val daiquiri = Recipe(CatalogueId("daiquiri"), "Daiquiri", Method.Shaken,
    List(RecipeLine(rum, Millilitres(60)), RecipeLine(lime, Millilitres(20))), solidsKcal = 40, Nil, "test",
    estimate = false)
}
