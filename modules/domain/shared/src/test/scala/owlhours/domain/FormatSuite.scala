package owlhours.domain

class FormatSuite extends munit.FunSuite {
  test("numbers format the same on JVM and Scala.js") {
    assertEquals(Format.number(5.0), "5")
    assertEquals(Format.number(-0.1), "-0.1")
    assertEquals(Format.number(2.5), "2.5")
    assertEquals(Format.number(0.75), "0.75")
  }
}
