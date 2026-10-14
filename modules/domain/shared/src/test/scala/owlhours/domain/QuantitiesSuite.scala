package owlhours.domain

class QuantitiesSuite extends munit.FunSuite:
  test("Abv accepts 0 and 100 and rejects outside") {
    assertEquals(Abv.from(0).map(_.unwrap), Right(0.0))
    assertEquals(Abv.from(100).map(_.unwrap), Right(100.0))
    assertEquals(Abv.from(-0.1), Left("ABV must be between 0 and 100 %, got -0.1"))
    assertEquals(Abv.from(101), Left("ABV must be between 0 and 100 %, got 101"))
  }

  test("Millilitres must be strictly positive") {
    assertEquals(Millilitres.from(0), Left("Volume must be above 0 and at most 5000 ml, got 0"))
    assertEquals(Millilitres.from(500).map(_.unwrap), Right(500.0))
  }

  test("non-finite numbers are rejected with a clear message") {
    assertEquals(Abv.from(Double.NaN), Left("ABV must be a number"))
    assertEquals(BodyWeight.from(Double.PositiveInfinity), Left("Weight must be a number"))
  }

  test("body measurements have human ranges") {
    assert(BodyWeight.from(29.9).isLeft)
    assert(BodyWeight.from(80).isRight)
    assert(Height.from(119).isLeft)
    assert(Height.from(180).isRight)
    assertEquals(Age.from(17), Left("Age must be between 18 and 110 years, got 17"))
    assertEquals(Age.from(30).map(_.unwrap), Right(30))
  }

  test("apply throws IllegalArgumentException with the same message") {
    val e = intercept[IllegalArgumentException](Kcal(-1))
    assertEquals(e.getMessage, "Energy must be between 0 and 5000 kcal, got -1")
  }

  test("ids") {
    assertEquals(CatalogueId.from("negroni").map(_.unwrap), Right("negroni"))
    assertEquals(CatalogueId.from("Negroni!"), Left("Catalogue id must be a lowercase slug (a-z, 0-9, -), got Negroni!"))
    assertEquals(SessionId.from(""), Left("Session id must not be empty"))
  }
