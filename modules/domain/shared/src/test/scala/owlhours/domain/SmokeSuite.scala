package owlhours.domain

class SmokeSuite extends munit.FunSuite {
  test("domain package is reachable on this platform") {
    assertEquals(owlhours.domain.appName, "Owl Hours")
  }
}
