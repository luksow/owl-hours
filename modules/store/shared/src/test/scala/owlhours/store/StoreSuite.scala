package owlhours.store

import cats.effect.SyncIO
import java.time.{Instant, LocalDate}
import owlhours.domain.*
import scala.collection.mutable

class StoreSuite extends munit.FunSuite {
  final class Memory(initial: (String, String)*) extends KeyValue[SyncIO] {
    val data                            = mutable.Map.from(initial)
    def get(key: String)                = SyncIO(data.get(key))
    def set(key: String, value: String) = SyncIO(data.update(key, value))
    def remove(key: String)             = SyncIO(data.remove(key)).void
    def available                       = SyncIO.pure(true)
    def keys                            = SyncIO(data.keys.toList)
  }

  private val t0      = Instant.parse("2026-10-14T20:00:00Z")
  private val profile = Profile(Sex.Female, LocalDate.of(1997, 1, 1), BodyWeight(64.5), Height(168))
  private val beer = DrinkSnapshot(
    Some(CatalogueId("pale-lager")),
    "Pale lager / Pilsner",
    Millilitres(500),
    Abv(5),
    KcalPerLitre(404.15),
    Nil
  )
  private val negroni = DrinkSnapshot(
    Some(CatalogueId("negroni")),
    "Negroni (×1.5)",
    Millilitres(148.5),
    Abv(24.5),
    KcalPerLitre(1839.4),
    List(ComponentLine("Gin", Millilitres(45), Abv(40)))
  )
  private val session = Session(
    SessionId("s-1"),
    t0,
    Vector(
      Intake.Meal(Kcal(900), t0),
      Intake.Drink(beer, t0.plusSeconds(600), Minutes(30)),
      Intake.Water(Millilitres(250), t0.plusSeconds(3600), Minutes(5)),
      Intake.Drink(negroni, t0.plusSeconds(5400), Minutes(15))
    )
  )

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
    val kv     = Memory(Store.Keys.profile -> """{"sex":"Female","age":12}""", Store.Keys.disclaimer -> "true")
    val loaded = Store(kv).load.unsafeRunSync()
    assertEquals(loaded.persisted.profile, None)
    assertEquals(loaded.persisted.disclaimerAccepted, true)
    assertEquals(loaded.problems.size, 1)
    assert(loaded.problems.head.startsWith("Could not read your profile"), loaded.problems.head)
    val copies = kv.data.keys.filter(_.startsWith(s"oh.recovery.${Store.Keys.profile}."))
    assertEquals(copies.size, 1)
    assertEquals(kv.data(copies.head), """{"sex":"Female","age":12}""")
    // no decoder internals in a message meant for people
    assert(
      !loaded.problems.head.contains("DecodingFailure") && !loaded.problems.head.contains("DownField"),
      loaded.problems.head
    )
  }

  test("an unknown intake type is a readable problem, not a crash") {
    val kv = Memory(
      Store.Keys.sessions -> """[{"id":"s-1","startedAt":"2026-10-14T20:00:00Z","intakes":[{"type":"snack"}]}]"""
    )
    val loaded = Store(kv).load.unsafeRunSync()
    assertEquals(loaded.persisted.sessions, Vector.empty)
    assert(loaded.problems.exists(_.contains("unknown intake type 'snack'")), loaded.problems.toString)
  }

  test("review: a corrupt value is removed after its recovery copy, so it is reported only once") {
    val kv    = Memory(Store.Keys.settings -> """{"bacUnit":"Furlongs"}""")
    val store = Store(kv)
    assertEquals(store.load.unsafeRunSync().problems.size, 1)
    assertEquals(kv.data.get(Store.Keys.settings), None)
    assertEquals(store.load.unsafeRunSync(), Loaded(Persisted.empty, Nil))
    assertEquals(kv.data.keys.count(_.startsWith("oh.recovery.")), 1)
  }

  test("review: storage that cannot be used is reported to the user") {
    val blocked = new KeyValue[SyncIO] {
      def get(key: String)                = SyncIO.pure(None)
      def set(key: String, value: String) = SyncIO.unit
      def remove(key: String)             = SyncIO.unit
      def available                       = SyncIO.pure(false)
      def keys                            = SyncIO.pure(Nil)
    }
    val loaded = Store(blocked).load.unsafeRunSync()
    assertEquals(loaded.problems, List(Store.StorageUnavailable))
  }

  test("review: a stored night without intakes is dropped on load instead of crashing later") {
    val kv = Memory(Store.Keys.sessions -> """[{"id":"s-1","startedAt":"2026-10-14T20:00:00Z","intakes":[]}]""")
    assertEquals(Store(kv).load.unsafeRunSync().persisted.sessions, Vector.empty)
  }

  test("instants saved in every form older versions wrote still load") {
    val kv = Memory("oh.v1.sessions" -> """[{"id":"s-1","startedAt":"2026-10-14T20:00:00Z","intakes":[
      {"type":"meal","kcal":900,"at":"2026-10-14T20:00:00.123Z"},
      {"type":"meal","kcal":500,"at":"2026-10-14T20:30:00.123456Z"}]}]""")
    val loaded = Store(kv).load.unsafeRunSync()
    assertEquals(loaded.problems, Nil)
    assertEquals(
      loaded.persisted.sessions.head.intakes.map(_.startsAt),
      Vector(Instant.parse("2026-10-14T20:00:00.123Z"), Instant.parse("2026-10-14T20:30:00.123456Z"))
    )
  }

  test("nights saved before manual ends existed still load, and an end is saved and loaded") {
    val kv = Memory(
      "oh.v1.sessions" ->
        """[{"id":"s-1","startedAt":"2026-10-14T20:00:00Z","intakes":[{"type":"meal","kcal":900,"at":"2026-10-14T20:00:00Z"}]}]"""
    )
    val store  = Store(kv)
    val loaded = store.load.unsafeRunSync()
    assertEquals(loaded.problems, Nil)
    assertEquals(loaded.persisted.sessions, Vector(Session(SessionId("s-1"), t0, Vector(Intake.Meal(Kcal(900), t0)))))
    val ended = session.copy(endedAt = Some(t0.plusSeconds(7200)))
    store.saveSessions(Vector(ended)).unsafeRunSync()
    assertEquals(store.load.unsafeRunSync().persisted.sessions, Vector(ended))
  }

  test("saveAll writes every key, and a missing profile removes the stored one") {
    val kv    = Memory()
    val store = Store(kv)
    val all   = Persisted(disclaimerAccepted = true, Some(profile), Settings(BacUnit.MgPer100ml), Vector(session))
    store.saveAll(all).unsafeRunSync()
    assertEquals(store.load.unsafeRunSync().persisted, all)
    store.saveAll(all.copy(profile = None)).unsafeRunSync()
    assertEquals(kv.data.get("oh.v1.profile"), None)
  }

  test("settings saved before the standard drink existed load with the 10 g default; a choice round-trips") {
    val store  = Store(Memory("oh.v1.settings" -> """{"bacUnit":"MgPer100ml"}"""))
    val loaded = store.load.unsafeRunSync()
    assertEquals(loaded.problems, Nil)
    assertEquals(loaded.persisted.settings, Settings(BacUnit.MgPer100ml, StandardDrink.Grams10))
    store.saveSettings(Settings(BacUnit.Permille, StandardDrink.Grams14)).unsafeRunSync()
    assertEquals(store.load.unsafeRunSync().persisted.settings, Settings(BacUnit.Permille, StandardDrink.Grams14))
  }

  test("clear removes every Owl Hours key, recovery copies included, and nothing else") {
    val kv = Memory("oh.v1.profile" -> "{}", "oh.recovery.oh.v1.sessions.1" -> "[", "other.app" -> "x")
    Store(kv).clear.unsafeRunSync()
    assertEquals(kv.data.keySet.toSet, Set("other.app"))
  }

  test("the install hint's dismissal is remembered, and delete-all forgets it") {
    val kv    = Memory()
    val store = Store(kv)
    assertEquals(store.loadInstallHintDismissed.unsafeRunSync(), false)
    store.saveInstallHintDismissed.unsafeRunSync()
    assertEquals(kv.data.get("oh.v1.installHint"), Some("dismissed"))
    assertEquals(store.loadInstallHintDismissed.unsafeRunSync(), true)
    store.clear.unsafeRunSync()
    assertEquals(store.loadInstallHintDismissed.unsafeRunSync(), false)
  }

  test("a snapshot saved before melt water existed loads with none; a new one round-trips its melt water") {
    val old = """[{"id":"s-1","startedAt":"2026-10-14T20:00:00Z","intakes":[{"type":"drink","snapshot":""" +
      """{"catalogueId":"negroni","name":"Negroni","volume":99,"abv":24.5,"kcalPerLitre":1839.4,"components":[]},""" +
      """"start":"2026-10-14T20:10:00Z","duration":20}]}]"""
    val loaded = Store(Memory("oh.v1.sessions" -> old)).load.unsafeRunSync()
    assertEquals(loaded.problems, Nil)
    val drink = loaded.persisted.sessions.head.intakes.head.asInstanceOf[Intake.Drink]
    assertEquals(drink.snapshot.meltWater, None)
    val withMelt = drink.copy(snapshot = drink.snapshot.copy(meltWater = Some(Millilitres(9))))
    val kv       = Memory()
    Store(kv).saveSessions(Vector(Session(SessionId("s-1"), t0, Vector(withMelt)))).unsafeRunSync()
    assertEquals(Store(kv).load.unsafeRunSync().persisted.sessions.head.intakes.head, withMelt)
  }
}
