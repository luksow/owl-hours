package owlhours.app.logic

import cats.data.NonEmptyList
import owlhours.catalogue.*
import owlhours.domain.*

class RecipeEditSuite extends munit.FunSuite {
  private def spirit(id: String, name: String, abv: Double, kcal: Double) =
    Product(
      CatalogueId(id),
      name,
      Category.Spirit,
      Abv(abv),
      KcalPerLitre(kcal),
      false,
      List(Serving("shot", Millilitres(40))),
      Nil,
      "test",
      false
    )
  private val gin      = spirit("gin", "Gin", 40, 2200)
  private val campari  = spirit("campari", "Campari", 25, 2500)
  private val vermouth = spirit("vermouth", "Sweet vermouth", 15, 1500)
  private val negroni = Recipe(
    CatalogueId("negroni"),
    "Negroni",
    Method.Built,
    List(RecipeLine(gin, Millilitres(30)), RecipeLine(campari, Millilitres(30)), RecipeLine(vermouth, Millilitres(30))),
    0,
    Nil,
    "test",
    false
  )

  test("defaults are the recipe's amounts, optionally scaled") {
    assertEquals(RecipeEdit.defaults(negroni, 1.0), List("30", "30", "30"))
    assertEquals(RecipeEdit.defaults(negroni, 1.5), List("45", "45", "45"))
  }

  test("unchanged amounts give the standard drink") {
    val s = RecipeEdit.resolve(negroni, List("30", "30", "30")).toOption.get
    assertEquals(s.name, "Negroni")
    assertEquals(s.components.map(_.volume.unwrap), List(30.0, 30.0, 30.0))
  }

  test("a heavy hand on one ingredient is a custom drink") {
    val s = RecipeEdit.resolve(negroni, List("50", "30", "30")).toOption.get
    assertEquals(s.name, "Negroni (custom)")
    assertEquals(s.components.map(_.volume.unwrap), List(50.0, 30.0, 30.0))
  }

  test("0 ml leaves an ingredient out; all zero is an error") {
    val s = RecipeEdit.resolve(negroni, List("60", "30", "0")).toOption.get
    assertEquals(s.components.map(_.name), List("Gin", "Campari"))
    assertEquals(
      RecipeEdit.resolve(negroni, List("0", "0", "0")),
      Left(NonEmptyList.one("Add at least one ingredient"))
    )
  }

  test("every bad line is reported, by ingredient name") {
    assertEquals(
      RecipeEdit.resolve(negroni, List("abc", "", "0,5")).swap.toOption.get.toList,
      List("Gin: Amount must be a number", "Campari: Amount must be a number")
    )
  }

  test("too much in total is explained, not thrown") {
    val e = RecipeEdit.resolve(negroni, List("3000", "3000", "0")).swap.toOption.get.head
    assert(e.startsWith("Negroni"), e)
  }
}
