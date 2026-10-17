package owlhours.domain

import java.time.*
import java.time.format.DateTimeParseException

/**
 * Literal JDK results. On the JVM this runs against the JDK; on Node against Owl Hours' own java.time
 * (modules/domain/js), so both must give the same answers.
 */
class JavaTimeSuite extends munit.FunSuite {
  test("instants before the epoch, and milliseconds") {
    val t = Instant.ofEpochMilli(-1)
    assertEquals((t.getEpochSecond, t.getNano, t.toEpochMilli), (-1L, 999000000, -1L))
    assertEquals(t.toString, "1969-12-31T23:59:59.999Z")
    assertEquals(Instant.ofEpochSecond(10, -1).toString, "1970-01-01T00:00:09.999999999Z")
    assertEquals(Instant.EPOCH.plusSeconds(86399).toString, "1970-01-01T23:59:59Z")
  }

  test("parsing instants: seconds required, fractions optional; impossible dates rejected") {
    assertEquals(Instant.parse("2026-10-14T20:00:00.5Z").getNano, 500000000)
    assertEquals(Instant.parse("2026-10-14T24:00:00Z"), Instant.parse("2026-10-15T00:00:00Z")) // the end of a day
    for bad <- List("2026-10-14T20:00Z", "2026-02-30T00:00:00Z", "2026-10-14T24:00:01Z", "2026-10-14 20:00:00Z", "2026-10-14T20:00:00") do
      assert(scala.util.Try(Instant.parse(bad)).failed.toOption.exists(_.isInstanceOf[DateTimeParseException]), s"$bad -> ${scala.util.Try(Instant.parse(bad))}")
  }

  test("instants compare, equal and hash by value") {
    val a = Instant.parse("2026-10-14T20:00:00Z")
    val b = Instant.ofEpochMilli(a.toEpochMilli)
    assert(a == b && a.hashCode == b.hashCode && a.compareTo(b) == 0)
    assert(a.isBefore(a.plusSeconds(1)) && a.isAfter(a.minusSeconds(1)))
    assertEquals(a.plus(Duration.ofMinutes(90)).minus(Duration.ofHours(1)), a.plusSeconds(1800))
    assertEquals(a.atOffset(ZoneOffset.UTC).toLocalDate, LocalDate.of(2026, 10, 14))
  }

  test("durations: between, units and text") {
    val a = Instant.parse("2026-10-14T20:00:00Z")
    assertEquals(Duration.between(a, a.plusSeconds(5430)).toMinutes, 90L)
    assertEquals(Duration.ofMinutes(90).toString, "PT1H30M")
    assertEquals(Duration.ofMillis(-1500).toString, "PT-1.5S")
    assertEquals(Duration.ZERO.toString, "PT0S")
    assertEquals(Duration.ofHours(2).plus(Duration.ofMinutes(5)).toMinutes, 125L)
  }

  test("dates: validation, epoch days, text and year arithmetic") {
    intercept[DateTimeException](LocalDate.of(2023, 2, 29))
    intercept[DateTimeException](LocalDate.of(2023, 13, 1))
    assertEquals(LocalDate.ofEpochDay(-1), LocalDate.of(1969, 12, 31))
    assertEquals(LocalDate.of(2000, 3, 1).toEpochDay, 11017L)
    assertEquals(LocalDate.of(2024, 2, 29).minusYears(1), LocalDate.of(2023, 2, 28))
    assertEquals(LocalDate.of(-5, 1, 1).toString, "-0005-01-01")
    assertEquals(LocalDate.of(12345, 1, 1).toString, "+12345-01-01")
    assert(LocalDate.of(2026, 1, 1).isAfter(LocalDate.of(2025, 12, 31)))
  }

  test("periods count whole years and months, then days") {
    val p = Period.between(LocalDate.of(1996, 1, 31), LocalDate.of(2026, 2, 28))
    assertEquals((p.getYears, p.getMonths, p.getDays), (30, 0, 28))
    val q = Period.between(LocalDate.of(2000, 3, 15), LocalDate.of(1999, 1, 20))
    assertEquals((q.getYears, q.getMonths, q.getDays), (-1, -1, -26))
    assertEquals(Period.between(LocalDate.of(2008, 10, 17), LocalDate.of(2026, 10, 16)).getYears, 17)
  }

  test("UTC date-times read back field by field") {
    val dt = LocalDateTime.ofEpochSecond(1792100458L, 949000000, ZoneOffset.UTC)
    assertEquals((dt.getHour, dt.getMinute, dt.getSecond, dt.toLocalDate), (21, 40, 58, LocalDate.of(2026, 10, 15)))
  }
}
