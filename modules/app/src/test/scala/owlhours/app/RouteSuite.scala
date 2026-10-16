package owlhours.app

import owlhours.domain.SessionId

class RouteSuite extends munit.FunSuite:
  test("every route parses back from its own hash") {
    for r <- List(Route.Tonight, Route.AddDrink, Route.Water, Route.Food, Route.Log, Route.History, Route.Settings,
        Route.Night(SessionId("night-1792100458949"))) do
      assertEquals(Route.parse(r.hash), r)
  }

  test("unknown and empty hashes go to Tonight; a history link without an id goes to History") {
    assertEquals(Route.parse(""), Route.Tonight)
    assertEquals(Route.parse("#/nope"), Route.Tonight)
    assertEquals(Route.parse("#/history/"), Route.History)
  }
