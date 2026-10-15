package owlhours.domain

import java.time.{Instant, LocalDate}

class ProfileSuite extends munit.FunSuite:
  private val p = Profile(Sex.Female, LocalDate.of(1996, 10, 15), BodyWeight(62), Height(166))

  test("age is whole years at the given moment, turning over on the birthday (UTC)") {
    assertEquals(p.ageAt(Instant.parse("2026-10-14T23:59:00Z")), 29)
    assertEquals(p.ageAt(Instant.parse("2026-10-15T00:00:00Z")), 30)
  }
