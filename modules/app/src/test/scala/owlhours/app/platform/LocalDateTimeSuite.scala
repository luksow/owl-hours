package owlhours.app.platform

import java.time.Instant

class LocalDateTimeSuite extends munit.FunSuite:
  test("format then parse returns the same minute in the local zone") {
    val t = Instant.parse("2026-10-14T21:37:42Z")
    val s = LocalDateTime.format(t)
    assert(s.matches("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}"""), s)
    assertEquals(LocalDateTime.parse(s), Some(Instant.parse("2026-10-14T21:37:00Z")))
  }

  test("empty or malformed input is not a time") {
    assertEquals(LocalDateTime.parse(""), None)
    assertEquals(LocalDateTime.parse("23:40"), None)
    assertEquals(LocalDateTime.parse("2026-13-40T99:99"), None)
  }

  test("a day label is human") {
    assert(LocalDateTime.dayLabel(Instant.parse("2026-10-14T21:00:00Z")).nonEmpty)
  }
