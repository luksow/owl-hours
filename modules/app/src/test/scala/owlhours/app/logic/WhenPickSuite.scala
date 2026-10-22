package owlhours.app.logic

import java.time.Instant
import WhenPick.*

class WhenPickSuite extends munit.FunSuite {
  private val now    = Instant.parse("2026-10-17T21:47:20Z")
  private val format = (t: Instant) => t.toString.substring(0, 16) // UTC "yyyy-MM-ddTHH:mm" stands in for local time
  private val parse = (s: String) =>
    scala.util.Try(Instant.parse(s + ":00Z")).toEither.left.map(_ => "Pick a date and time")

  test("a chip shows its time in the field") {
    assertEquals(text(When.Ago(0), now, format), "2026-10-17T21:47")
    assertEquals(text(When.Ago(30), now, format), "2026-10-17T21:17")
    assertEquals(text(When.At("2026-10-17T19:05"), now, format), "2026-10-17T19:05")
  }

  test("'Now' follows the clock: the field and Add use the current moment") {
    val later = now.plusSeconds(90)
    assertEquals(text(When.Ago(0), later, format), "2026-10-17T21:48")
    assertEquals(resolve(When.Ago(0), later, parse), Right(later))
  }

  test("typing a time a chip would show selects that chip; any other time is a picked time") {
    assertEquals(typed("2026-10-17T21:32", now, format), When.Ago(15))
    assertEquals(typed("2026-10-17T21:47", now, format), When.Ago(0))
    assertEquals(typed("2026-10-17T20:00", now, format), When.At("2026-10-17T20:00"))
    assertEquals(typed("", now, format), When.At(""))
  }

  test("a picked time resolves through the parser and is refused in the future") {
    assertEquals(resolve(When.At("2026-10-17T20:00"), now, parse), Right(Instant.parse("2026-10-17T20:00:00Z")))
    assert(resolve(When.At("2026-10-18T09:00"), now, parse).isLeft)
    assert(resolve(When.At(""), now, parse).isLeft)
  }

  test("editing a drink lined up by Same again keeps its later time; other future times are still refused") {
    val queued = Instant.parse("2026-10-17T22:07:41.250Z") // the field shows minutes; the stored time has seconds
    assertEquals(resolve(When.At("2026-10-17T22:07"), now, parse, keep = Some(queued)), Right(queued))
    assert(resolve(When.At("2026-10-17T22:30"), now, parse, keep = Some(queued)).isLeft)
  }
}
