package owlhours.store

import java.time.{Instant, LocalDate}
import owlhours.domain.*

class BackupSuite extends munit.FunSuite {
  private val t0      = Instant.parse("2026-10-14T20:00:00Z")
  private val profile = Profile(Sex.Female, LocalDate.of(1997, 1, 1), BodyWeight(64.5), Height(168))
  private val night   = Session(SessionId("night-1"), t0, Vector(Intake.Meal(Kcal(900), t0)), endedAt = Some(t0.plusSeconds(3600)))
  private val all     = Persisted(disclaimerAccepted = true, Some(profile), Settings(BacUnit.MgPer100ml, StandardDrink.Grams8), Vector(night))

  test("a backup reads back as the same data") {
    assertEquals(Backup.decode(Backup.encode(all, t0)), Right(all))
  }

  test("a backup says what it is and when it was made") {
    val text = Backup.encode(all, t0)
    assert(text.contains(""""format" : "owl-hours-backup""""), text.take(120))
    assert(text.contains(""""exportedAt" : "2026-10-14T20:00:00Z""""))
  }

  test("files that are not backups change nothing and say so") {
    for text <- List("", "not json", "{}", """{"format":"something-else","version":1}""", "\u0089PNG\r\n") do
      assertEquals(Backup.decode(text), Left(Backup.NotABackup), text)
  }

  test("a backup from a newer version is refused, not guessed at") {
    val newer = Backup.encode(all, t0).replace(""""version" : 1""", """"version" : 2""")
    assert(Backup.decode(newer).left.exists(_.contains("newer version")), Backup.decode(newer).toString)
  }

  test("a damaged night or a missing profile is refused with the part that failed") {
    val brokenNight = Backup.encode(all, t0).replaceAll(""""kcal" : [0-9.]+""", """"kcal" : -5""")
    assert(Backup.decode(brokenNight).left.exists(_.contains("nights")), Backup.decode(brokenNight).toString)
    val noProfile = Backup.encode(all.copy(profile = None), t0)
    assert(Backup.decode(noProfile).left.exists(_.contains("profile")), Backup.decode(noProfile).toString)
  }
}
