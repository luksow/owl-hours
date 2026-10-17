package owlhours.catalogue

import owlhours.domain.*

class CatalogueJsonSuite extends munit.FunSuite {
  private def doc(styles: String, products: String, cocktails: String, version: Int = 1) =
    s"""{"schemaVersion": $version, "styles": [$styles], "products": [$products], "cocktails": [$cocktails]}"""

  private val lager =
    """{"id": "pale-lager", "name": "Pale lager", "category": "Beer", "abv": 5, "abvMin": 4.2, "abvMax": 6,
      | "carbsPerLitre": 32, "servings": [{"label": "bottle", "ml": 330}], "aliases": ["pils"], "source": "test",
      | "estimate": false}""".stripMargin
  private val gin =
    """{"id": "gin", "name": "Gin", "category": "Spirit", "abv": 40, "kcalPer100ml": 221, "abvAdjustable": true,
      | "servings": [{"label": "shot", "ml": 40}], "aliases": [], "source": "test", "estimate": false}""".stripMargin
  private val tonic =
    """{"id": "tonic-water", "name": "Tonic water", "category": "Mixer", "abv": 0, "kcalPer100ml": 35,
      | "abvAdjustable": false, "servings": [{"label": "serve", "ml": 150}], "aliases": [], "source": "test",
      | "estimate": false}""".stripMargin
  private val gt =
    """{"id": "gin-tonic", "name": "Gin & Tonic", "method": "Built", "ingredients": [{"id": "gin", "ml": 50},
      | {"id": "tonic-water", "ml": 120}], "solidsKcal": 0, "aliases": ["G&T"], "source": "test",
      | "estimate": false}""".stripMargin

  test("a valid document loads, with cocktail ingredients linked to the products") {
    val c = Catalogue.fromJson(doc(lager, s"$gin, $tonic", gt)).fold(e => fail(e.toList.mkString("\n")), identity)
    assertEquals(c.styles.map(_.id.unwrap), List("pale-lager"))
    assertEquals(c.products.map(_.kcalPerLitre.unwrap), List(2210.0, 350.0))
    val recipe = c.recipes.head
    assertEquals(recipe.method, Method.Built)
    assert(recipe.lines.head.ingredient eq c.products.head, "ingredient should be the loaded product instance")
    assertEquals(c.get(CatalogueId("gin-tonic")).map(_.name), Some("Gin & Tonic"))
    assertEquals(c.inGroup(Group.Cocktails).map(_.id.unwrap), List("gin-tonic"))
    assertEquals(c.styles.head.servings, List(Serving("bottle", Millilitres(330))))
  }

  test("decoding problems are all reported, with their JSON path and the validator's message") {
    val badGin   = gin.replace("\"abv\": 40", "\"abv\": 101")
    val badLager = lager.replace("\"Beer\"", "\"Beeer\"")
    val errors   = Catalogue.fromJson(doc(badLager, s"$badGin, $tonic", gt)).swap.toOption.get.toList
    assertEquals(errors.size, 2, errors.mkString("\n"))
    assert(
      errors.exists(e => e.contains(".products[0].abv") && e.contains("ABV must be between 0 and 100 %, got 101")),
      errors.mkString("\n")
    )
    assert(
      errors.exists(e => e.contains(".styles[0].category") && e.contains("unknown category 'Beeer'")),
      errors.mkString("\n")
    )
  }

  test("integrity problems are all reported") {
    val misspelt   = gt.replace("\"tonic-water\"", "\"tonic-wtaer\"")
    val duplicate  = gin.replace("\"Gin\"", "\"Another gin\"")
    val outOfRange = lager.replace("\"abv\": 5", "\"abv\": 7")
    val tooLight   = tonic.replace("\"abv\": 0", "\"abv\": 10") // 35 kcal/100 ml cannot hold 10 % alcohol (≈ 55 kcal)
    val errors = Catalogue
      .fromJson(doc(outOfRange, s"$gin, $duplicate, $tooLight", misspelt, version = 2))
      .swap
      .toOption
      .get
      .toList
    assertEquals(
      errors.toSet,
      Set(
        "unsupported schemaVersion 2 (expected 1)",
        "styles[pale-lager]: default ABV 7 is outside its range 4.2–6",
        "products[tonic-water]: 35 kcal/100 ml is below the energy of its alcohol alone (55.2)",
        "duplicate id 'gin'",
        "cocktails[gin-tonic]: unknown ingredient 'tonic-wtaer'"
      )
    )
  }

  test("text that is not JSON is a single readable error, not an exception") {
    val errors = Catalogue.fromJson("{ not json").swap.toOption.get.toList
    assertEquals(errors.size, 1)
    assert(errors.head.startsWith("catalogue is not valid JSON"), errors.head)
  }

  test("review: cocktails that cannot be resolved are rejected at load time") {
    val empty = gt
      .replace("\"gin-tonic\"", "\"empty\"")
      .replace("[{\"id\": \"gin\", \"ml\": 50},\n {\"id\": \"tonic-water\", \"ml\": 120}]", "[]")
    val negative = gt.replace("\"solidsKcal\": 0", "\"solidsKcal\": -500")
    val errors   = Catalogue.fromJson(doc(lager, s"$gin, $tonic", s"$empty, $negative")).swap.toOption.get.toList
    assert(errors.contains("cocktails[empty]: needs at least one ingredient"), errors.mkString("\n"))
    assert(
      errors.contains("cocktails[gin-tonic]: solidsKcal must be between 0 and 1000, got -500"),
      errors.mkString("\n")
    )
  }

  test("review: every cocktail is resolved at its standard serve while loading") {
    val dense = gt
      .replace("\"ml\": 50}", "\"ml\": 1}")
      .replace("\"ml\": 120}", "\"ml\": 1}")
      .replace("\"solidsKcal\": 0", "\"solidsKcal\": 900")
    val errors = Catalogue.fromJson(doc(lager, s"$gin, $tonic", dense)).swap.toOption.get.toList
    assert(errors.exists(_.startsWith("cocktails[gin-tonic]: Gin & Tonic: Energy density")), errors.mkString("\n"))
  }
}
