package owlhours.app.logic

import java.time.Instant
import owlhours.domain.*

class EditsSuite extends munit.FunSuite:
  private val t0      = Instant.parse("2026-10-14T20:00:00Z")
  private val lager   = DrinkSnapshot(Some(CatalogueId("pale-lager")), "Pale lager", Millilitres(500), Abv(5), KcalPerLitre(404), Nil)
  private val negroni = DrinkSnapshot(Some(CatalogueId("negroni")), "Negroni", Millilitres(99), Abv(24.5), KcalPerLitre(1839),
    List(ComponentLine("Gin", Millilitres(30), Abv(40)), ComponentLine("Campari", Millilitres(30), Abv(25))))

  test("amounts are parsed the way people type them") {
    assertEquals(Amount.ml(" 500 "), Right(Millilitres(500)))
    assertEquals(Amount.drinkMl("0,5"), Left("Amounts are in ml — did you mean 500 ml?"))
    assertEquals(Amount.drinkMl("4"), Left("Amounts are in ml — did you mean 4000 ml?"))
    assertEquals(Amount.drinkMl("25"), Right(Millilitres(25)))
    assertEquals(Amount.ml(""), Left("Amount must be a number"))
    assertEquals(Amount.ml("abc"), Left("Amount must be a number"))
    assertEquals(Amount.ml("0"), Left("Volume must be above 0 and at most 5000 ml, got 0"))
    assertEquals(Amount.ml("6000"), Left("Volume must be above 0 and at most 5000 ml, got 6000"))
    assertEquals(Amount.minutes("45"), Right(Minutes(45)))
    assertEquals(Amount.kcal("650"), Right(Kcal(650)))
    assertEquals(Amount.abv("5,5"), Right(Abv(5.5)))
  }

  test("changing a cocktail's amount scales every ingredient; ABV and kcal/L stay") {
    val big = Edits.rescale(negroni, Millilitres(148.5))
    assertEquals(big.volume, Millilitres(148.5))
    assertEquals(big.components.map(_.volume.unwrap), List(45.0, 45.0))
    assertEquals((big.abv, big.kcalPerLitre), (negroni.abv, negroni.kcalPerLitre))
  }

  test("ABV can be edited for single drinks only, keeping non-alcohol calories") {
    val stronger = Edits.withAbv(lager, Abv(6)).toOption.get
    assertEquals(stronger.abv, Abv(6))
    assert(stronger.kcalPerLitre.unwrap > lager.kcalPerLitre.unwrap)
    assertEquals(Edits.withAbv(negroni, Abv(30)), Left("The ABV of a mixed drink follows its ingredients"))
  }

  test("an edit applies to the matching intake kind") {
    val drink = Intake.Drink(lager, t0, Minutes(30))
    val edited = Edits(drink, IntakeEdit(t0.plusSeconds(600), Some(Minutes(45)), Some(Millilitres(330)), Some(Abv(4.5)), None))
    assertEquals(edited.map(_.startsAt), Right(t0.plusSeconds(600)))
    edited.toOption.get match
      case Intake.Drink(s, _, d) =>
        assertEquals((s.volume, s.abv, d), (Millilitres(330), Abv(4.5), Minutes(45)))
      case other => fail(other.toString)
    assertEquals(Edits(Intake.Meal(Kcal(500), t0), IntakeEdit(t0, None, None, None, Some(Kcal(750)))),
      Right(Intake.Meal(Kcal(750), t0)))
    assertEquals(Edits(Intake.Water(Millilitres(250), t0, Minutes(5)), IntakeEdit(t0, None, Some(Millilitres(500)), None, None)),
      Right(Intake.Water(Millilitres(500), t0, Minutes(5))))
  }

  test("start times in the future are rejected (one minute of slack)") {
    assertEquals(Edits.notInFuture(t0.plusSeconds(30), t0), Right(t0.plusSeconds(30)))
    assertEquals(Edits.notInFuture(t0.plusSeconds(600), t0),
      Left("That's later than now — for last night, pick yesterday's date"))
    assertEquals(Edits.notInFuture(t0.plusSeconds(30 * 3600), t0), Left("That time is in the future"))
  }
