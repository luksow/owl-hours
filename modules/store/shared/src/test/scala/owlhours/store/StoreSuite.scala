package owlhours.store

import cats.effect.SyncIO
import java.time.Instant
import owlhours.domain.*
import scala.collection.mutable

class StoreSuite extends munit.FunSuite:
  final class Memory(initial: (String, String)*) extends KeyValue[SyncIO]:
    val data = mutable.Map.from(initial)
    def get(key: String)                = SyncIO(data.get(key))
    def set(key: String, value: String) = SyncIO(data.update(key, value))

  private val t0      = Instant.parse("2026-10-14T20:00:00Z")
  private val profile = Profile(Sex.Female, Age(29), BodyWeight(64.5), Height(168))
  private val beer    = DrinkSnapshot(Some(CatalogueId("pale-lager")), "Pale lager / Pilsner", Millilitres(500), Abv(5),
    KcalPerLitre(404.15), Nil)
  private val negroni = DrinkSnapshot(Some(CatalogueId("negroni")), "Negroni (×1.5)", Millilitres(148.5), Abv(24.5),
    KcalPerLitre(1839.4), List(ComponentLine("Gin", Millilitres(45), Abv(40))))
  private val session = Session(SessionId("s-1"), t0, Vector(
    Intake.Meal(Kcal(900), t0),
    Intake.Drink(beer, t0.plusSeconds(600), Minutes(30)),
    Intake.Water(Millilitres(250), t0.plusSeconds(3600), Minutes(5)),
    Intake.Drink(negroni, t0.plusSeconds(5400), Minutes(15))
  ))

  test("empty storage loads as a first run") {
    assertEquals(Store(Memory()).load.unsafeRunSync(), Loaded(Persisted.empty, Nil))
  }

  test("everything saved loads back unchanged") {
    val kv    = Memory()
    val store = Store(kv)
    (store.saveDisclaimer(true) *> store.saveProfile(profile) *> store.saveSettings(Settings(BacUnit.MgPer100ml)) *>
      store.saveSessions(Vector(session))).unsafeRunSync()
    assertEquals(
      store.load.unsafeRunSync(),
      Loaded(Persisted(disclaimerAccepted = true, Some(profile), Settings(BacUnit.MgPer100ml), Vector(session)), Nil)
    )
    assert(kv.data.keySet == Set(Store.Keys.disclaimer, Store.Keys.profile, Store.Keys.settings, Store.Keys.sessions))
  }

  test("an unreadable value is kept as a recovery copy, reported, and the rest still loads") {
    val kv = Memory(Store.Keys.profile -> """{"sex":"Female","age":12}""", Store.Keys.disclaimer -> "true")
    val loaded = Store(kv).load.unsafeRunSync()
    assertEquals(loaded.persisted.profile, None)
    assertEquals(loaded.persisted.disclaimerAccepted, true)
    assertEquals(loaded.problems.size, 1)
    assert(loaded.problems.head.startsWith("Could not read your profile"), loaded.problems.head)
    val copies = kv.data.keys.filter(_.startsWith(s"oh.recovery.${Store.Keys.profile}."))
    assertEquals(copies.size, 1)
    assertEquals(kv.data(copies.head), """{"sex":"Female","age":12}""")
  }

  test("an unknown intake type is a readable problem, not a crash") {
    val kv     = Memory(Store.Keys.sessions -> """[{"id":"s-1","startedAt":"2026-10-14T20:00:00Z","intakes":[{"type":"snack"}]}]""")
    val loaded = Store(kv).load.unsafeRunSync()
    assertEquals(loaded.persisted.sessions, Vector.empty)
    assert(loaded.problems.exists(_.contains("unknown intake type 'snack'")), loaded.problems.toString)
  }
