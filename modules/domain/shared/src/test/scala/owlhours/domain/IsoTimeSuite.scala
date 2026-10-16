package owlhours.domain

import java.time.{Instant, LocalDate}
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

class IsoTimeSuite extends munit.ScalaCheckSuite:
  private val instants = for
    seconds <- Gen.choose(Instant.parse("1900-01-01T00:00:00Z").getEpochSecond, Instant.parse("2200-01-01T00:00:00Z").getEpochSecond)
    nanos   <- Gen.oneOf(Gen.const(0), Gen.choose(0, 999).map(_ * 1000000), Gen.choose(0, 999999).map(_ * 1000), Gen.choose(0, 999999999))
  yield Instant.ofEpochSecond(seconds, nanos.toLong)

  property("instants are written exactly as Instant.toString writes them, and read back") {
    forAll(instants) { t =>
      assertEquals(IsoTime.formatInstant(t), t.toString)
      assertEquals(IsoTime.parseInstant(t.toString), Some(t))
    }
  }

  property("dates are written as LocalDate.toString writes them, and read back") {
    forAll(Gen.choose(LocalDate.of(1000, 1, 1).toEpochDay, LocalDate.of(9999, 12, 31).toEpochDay)) { day =>
      val d = LocalDate.ofEpochDay(day)
      assertEquals(IsoTime.formatDate(d), d.toString)
      assertEquals(IsoTime.parseDate(d.toString), Some(d))
    }
  }

  test("the browser's toISOString form is read (milliseconds, Z)") {
    assertEquals(IsoTime.parseInstant("2026-10-15T21:40:58.949Z"), Some(Instant.ofEpochMilli(1792100458949L)))
  }

  test("text that is not a real instant or date is rejected") {
    for s <- List("", "2026-02-30T00:00:00Z", "2026-10-14 20:00:00Z", "2026-10-14T24:00:00Z", "2026-10-14T20:60:00Z",
        "2026-10-14T20:00:00", "2026-10-14T20:00:00+02:00", "abc") do
      assertEquals(IsoTime.parseInstant(s), None, s)
    assertEquals(IsoTime.parseDate("2026-02-29"), None)
    assertEquals(IsoTime.parseDate("2024-02-29"), Some(LocalDate.of(2024, 2, 29)))
    assertEquals(IsoTime.parseDate("1996-1-1"), None)
  }

  test("local date-times are checked against the calendar") {
    assert(IsoTime.isLocalDateTime("2026-03-29T02:30"))
    assert(!IsoTime.isLocalDateTime("2026-02-30T20:00"))
    assert(!IsoTime.isLocalDateTime("2026-10-14T25:00"))
    assert(!IsoTime.isLocalDateTime("2026-10-14T20:00:00"))
  }
