package owlhours.app.logic

import java.time.{Instant, LocalDate}
import owlhours.domain.*

class SessionsSuite extends munit.FunSuite {
  private val profile = Profile(Sex.Male, LocalDate.of(1996, 1, 1), BodyWeight(80), Height(180))
  private val t0      = Instant.parse("2026-10-14T20:00:00Z")
  private def at(min: Long) = t0.plusSeconds(min * 60)
  private val pint    = DrinkSnapshot(None, "Pale lager", Millilitres(568), Abv(5), KcalPerLitre(404), Nil)
  private def drink(min: Long) = Intake.Drink(pint, at(min), Minutes(30))
  private def water(min: Long) = Intake.Water(Millilitres(250), at(min), Minutes(5))

  private def nights(ss: Vector[Session]) = ss.map(_.intakes.map(_.startsAt))

  test("the first intake starts a night named after it") {
    val ss = Sessions.add(profile, Vector.empty, drink(0))
    assertEquals(ss.map(_.id.unwrap), Vector(s"night-${at(0).toEpochMilli}"))
  }

  test("later intakes the same night join it, in time order, including ones logged in the past") {
    val ss = List(drink(0), drink(60), water(20)).foldLeft(Vector.empty[Session])(Sessions.add(profile, _, _))
    assertEquals(nights(ss), Vector(Vector(at(0), at(20), at(60))))
  }

  test("the next evening starts a new night") {
    val ss = List(drink(0), drink(24 * 60)).foldLeft(Vector.empty[Session])(Sessions.add(profile, _, _))
    assertEquals(nights(ss), Vector(Vector(at(0)), Vector(at(24 * 60))))
  }

  test("moving a drink to the next evening splits the night") {
    val ss    = List(drink(0), drink(60)).foldLeft(Vector.empty[Session])(Sessions.add(profile, _, _))
    val moved = Sessions.replace(profile, ss, drink(60), drink(24 * 60))
    assertEquals(nights(moved), Vector(Vector(at(0)), Vector(at(24 * 60))))
  }

  test("deleting removes exactly one of two identical intakes; an emptied night disappears") {
    val ss  = List(drink(0), water(10), water(10)).foldLeft(Vector.empty[Session])(Sessions.add(profile, _, _))
    val one = Sessions.delete(profile, ss, water(10))
    assertEquals(nights(one), Vector(Vector(at(0), at(10))))
    val solo = Sessions.add(profile, Vector.empty, drink(0))
    assertEquals(Sessions.delete(profile, solo, drink(0)), Vector.empty)
  }

  test("review: an entry that is no longer there is left alone (stale taps never hit a neighbour)") {
    val ss = List(drink(0), water(10)).foldLeft(Vector.empty[Session])(Sessions.add(profile, _, _))
    assert(Sessions.delete(profile, ss, water(99)) eq ss)
    assert(Sessions.replace(profile, ss, water(99), water(20)) eq ss)
  }

  test("nights that closed long before a change are kept as they are") {
    val old   = Sessions.add(profile, Vector.empty, drink(-7 * 24 * 60))
    val today = Sessions.add(profile, old, drink(0))
    assert(today.head eq old.head)
  }

  test("a night closes two hours after blood alcohol is back to ~0") {
    val s     = Sessions.add(profile, Vector.empty, drink(0)).head
    val close = Sessions.closesAt(profile, s)
    assert(close.isAfter(at(30 + 120)) && close.isBefore(at(12 * 60)), close.toString)
    assertEquals(Sessions.active(profile, Vector(s), close.minusSeconds(60)).map(_.id), Some(s.id))
    assertEquals(Sessions.active(profile, Vector(s), close.plusSeconds(60)), None)
  }

  test("an intake ends when it has been drunk; a meal ends when it is eaten") {
    assertEquals(Sessions.endOf(drink(10)), at(40))
    assertEquals(Sessions.endOf(Intake.Meal(Kcal(500), at(10))), at(10))
  }

  private def endedAfterOnePint = Sessions.end(profile, Sessions.add(profile, Vector.empty, drink(0)), at(240))

  test("a night can be ended once the estimate is back to ~0; the end closes it at once") {
    val ss = endedAfterOnePint
    assertEquals(ss.map(_.endedAt), Vector(Some(at(240))))
    assertEquals(Sessions.closesAt(profile, ss.last), at(240))
    assertEquals(Sessions.active(profile, ss, at(241)), None)
  }

  test("a night is not ended while the estimate is above ~0, and an ended night is not ended again") {
    val open = Sessions.add(profile, Vector.empty, drink(0))
    assertEquals(Sessions.end(profile, open, at(40)), open)
    assert(!Sessions.canEnd(profile, open.last, at(40)))
    assert(Sessions.canEnd(profile, open.last, at(240)))
    assertEquals(Sessions.end(profile, endedAfterOnePint, at(250)), endedAfterOnePint)
    assertEquals(Sessions.end(profile, Vector.empty, at(250)), Vector.empty)
  }

  test("a drink after the end starts a new night; the ended night keeps its end") {
    val next = Sessions.add(profile, endedAfterOnePint, drink(250))
    assertEquals(nights(next), Vector(Vector(at(0)), Vector(at(250))))
    assertEquals(next.map(_.endedAt), Vector(Some(at(240)), None))
  }

  test("a drink moved to before the end rejoins the ended night") {
    val next  = Sessions.add(profile, endedAfterOnePint, drink(250))
    val moved = Sessions.replace(profile, next, drink(250), drink(60))
    assertEquals(nights(moved), Vector(Vector(at(0), at(60))))
    // two pints are still above ~0 at the old end, so the end no longer holds and the night is open again
    assertEquals(moved.map(_.endedAt), Vector(None))
  }

  test("regrouping everything keeps manual ends") {
    val ss = Sessions.add(profile, endedAfterOnePint, drink(250))
    assertEquals(Sessions.regroupAll(profile, ss), ss)
  }

  test("an ended night is closed at once, even to a clock that has not yet passed the end") {
    assertEquals(Sessions.active(profile, endedAfterOnePint, at(239)), None)
    assertEquals(Sessions.active(profile, endedAfterOnePint, at(240)), None)
  }


  test("review: a drink logged as starting before the end reopens the night, so Tonight keeps counting it") {
    val ss = Sessions.add(profile, endedAfterOnePint, drink(225))
    assertEquals(nights(ss), Vector(Vector(at(0), at(225))))
    assertEquals(ss.map(_.endedAt), Vector(None))
    assert(Sessions.active(profile, ss, at(250)).isDefined)
  }

  test("review: an end that no longer holds does not split off a later drink") {
    val next  = Sessions.add(profile, endedAfterOnePint, drink(250))
    val moved = Sessions.add(profile, next, drink(225))
    assertEquals(nights(moved), Vector(Vector(at(0), at(225), at(250))))
    assertEquals(moved.map(_.endedAt), Vector(None))
  }

  test("a night still above ~0 a day after its last drink stays open until two hours after Tonight's back-to time") {
    val shot    = DrinkSnapshot(None, "Vodka", Millilitres(40), Abv(40), KcalPerLitre(2210), Nil)
    val session = Session(SessionId("x"), at(0), Vector.tabulate(22)(k => Intake.Drink(shot, at(k * 6L), Minutes(2))))
    val lastEnd = session.intakes.map(Sessions.endOf).maxBy(_.toEpochMilli)
    val backTo  = Tonight.of(profile, Some(session), at(300)).summary.backToZeroBy.get
    assert(backTo.isAfter(lastEnd.plusSeconds(24 * 3600)), s"the fixture must outlast a day: $backTo")
    // the close rule samples every 5 min and Tonight every 2, so they may differ by one 5-min step
    assert(!Sessions.closesAt(profile, session).isBefore(backTo.plus(Sessions.CloseAfter).minusSeconds(5 * 60)),
      s"${Sessions.closesAt(profile, session)} vs back-to $backTo")
  }
}
