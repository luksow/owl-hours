package owlhours.domain

import java.time.Instant

class DomainSuite extends munit.FunSuite:
  private val beer =
    DrinkSnapshot(None, "Lager", Millilitres(500), Abv(5), KcalPerLitre(430), Nil)

  test("ethanol grams = ml × ABV/100 × 0.789") {
    assertEqualsDouble(beer.ethanolGrams, 19.725, 1e-9)
  }

  test("Intake.startsAt is the start time for every case") {
    val t = Instant.parse("2026-10-14T20:00:00Z")
    assertEquals(Intake.Drink(beer, t, Minutes(20)).startsAt, t)
    assertEquals(Intake.Water(Millilitres(250), t, Minutes(5)).startsAt, t)
    assertEquals(Intake.Meal(Kcal(700), t).startsAt, t)
  }

  test("BAC unit conversions") {
    assertEqualsDouble(BacUnit.Permille.fromModel(52.5, 0.2), 0.5, 1e-12)
    assertEqualsDouble(BacUnit.PercentBac.fromModel(52.5, 0.2), 0.0525, 1e-12)
    assertEqualsDouble(BacUnit.MgPer100ml.fromModel(52.5, 0.2), 52.5, 1e-12)
    assertEqualsDouble(BacUnit.BreathMgPerL.fromModel(52.5, 0.2), 0.2, 1e-12)
    assertEquals(BacUnit.values.map(_.symbol).toList, List("‰", "%", "mg/100 ml", "mg/L"))
  }
