package owlhours.app.logic

import java.time.Instant
import owlhours.domain.*

class SessionsSuite extends munit.FunSuite:
  private val profile = Profile(Sex.Male, Age(30), BodyWeight(80), Height(180))
  private val t0      = Instant.parse("2026-10-14T20:00:00Z")
  private def at(min: Long) = t0.plusSeconds(min * 60)
  private val pint    = DrinkSnapshot(None, "Pale lager", Millilitres(568), Abv(5), KcalPerLitre(404), Nil)
  private def drink(min: Long) = Intake.Drink(pint, at(min), Minutes(30))

  test("the first intake starts a night") {
    val ss = Sessions.addIntake(profile, Vector.empty, drink(0), at(5), SessionId("a"))
    assertEquals(ss.map(_.id.unwrap), Vector("a"))
    assertEquals(ss.head.startedAt, at(0))
  }

  test("later intakes the same night join it, including drinks logged in the past") {
    val one = Sessions.addIntake(profile, Vector.empty, drink(0), at(5), SessionId("a"))
    val two = Sessions.addIntake(profile, one, drink(60), at(70), SessionId("b"))
    val three = Sessions.addIntake(profile, two, Intake.Water(Millilitres(250), at(20), Minutes(5)), at(75), SessionId("c"))
    assertEquals(three.size, 1)
    assertEquals(three.head.intakes.size, 3)
  }

  test("a night closes two hours after blood alcohol is back to ~0") {
    val s     = Session(SessionId("a"), at(0), Vector(drink(0)))
    val close = Sessions.closesAt(profile, s)
    assert(close.isAfter(at(30 + 120)), s"closes at $close")
    assert(close.isBefore(at(12 * 60)), s"closes at $close")
    assertEquals(Sessions.active(profile, Vector(s), close.minusSeconds(60)).map(_.id), Some(SessionId("a")))
    assertEquals(Sessions.active(profile, Vector(s), close.plusSeconds(60)), None)
  }

  test("the next evening starts a new night") {
    val one = Sessions.addIntake(profile, Vector.empty, drink(0), at(5), SessionId("a"))
    val nextDay = at(24 * 60)
    val two = Sessions.addIntake(profile, one, drink(24 * 60), nextDay, SessionId("b"))
    assertEquals(two.map(_.id.unwrap), Vector("a", "b"))
  }

  test("an intake ends when it has been drunk; a meal ends when it is eaten") {
    assertEquals(Sessions.endOf(drink(10)), at(40))
    assertEquals(Sessions.endOf(Intake.Meal(Kcal(500), at(10))), at(10))
  }
