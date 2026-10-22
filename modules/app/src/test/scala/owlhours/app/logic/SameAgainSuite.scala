package owlhours.app.logic

import java.time.{Instant, LocalDate}
import owlhours.domain.*

class SameAgainSuite extends munit.FunSuite {
  private val profile       = Profile(Sex.Male, LocalDate.of(1990, 1, 1), BodyWeight(85), Height(182))
  private val t0            = Instant.parse("2026-10-22T18:00:00Z")
  private def at(min: Long) = t0.plusSeconds(min * 60)
  private val ipa           = DrinkSnapshot(None, "American IPA", Millilitres(330), Abv(6.5), KcalPerLitre(567), Nil)
  private val shot          = DrinkSnapshot(None, "Vodka", Millilitres(40), Abv(40), KcalPerLitre(0), Nil)
  private def night(is: Intake*) = Session(SessionId("n"), is.head.startsAt, is.toVector)

  test("no drink yet: nothing to repeat") {
    assertEquals(SameAgain.next(None, at(0)), None)
    assertEquals(SameAgain.next(Some(night(Intake.Water(Millilitres(250), at(0), Minutes(5)))), at(10)), None)
  }

  test("the last drink is finished: the same drink starts now, for as long as the last one took") {
    val s = night(Intake.Drink(ipa, at(0), Minutes(20)))
    assertEquals(SameAgain.next(Some(s), at(45)), Some(Intake.Drink(ipa, at(45), Minutes(20))))
  }

  test("a drink is still going: the next one starts when it ends, not on top of it") {
    val s = night(Intake.Drink(ipa, at(0), Minutes(20)))
    assertEquals(SameAgain.next(Some(s), at(5)), Some(Intake.Drink(ipa, at(20), Minutes(20))))
  }

  test("it waits for whichever drink ends last, even if another one started later") {
    val s = night(Intake.Drink(ipa, at(0), Minutes(40)), Intake.Drink(shot, at(10), Minutes(1)))
    assertEquals(SameAgain.next(Some(s), at(12)), Some(Intake.Drink(shot, at(40), Minutes(1))))
  }

  test("water and food do not hold the next drink back") {
    val s = night(Intake.Drink(ipa, at(0), Minutes(20)), Intake.Water(Millilitres(330), at(25), Minutes(30)))
    assertEquals(SameAgain.next(Some(s), at(30)), Some(Intake.Drink(ipa, at(30), Minutes(20))))
  }

  /** Five quick taps, as a colleague did: each one lines up behind the last. */
  private def taps(n: Int, now: Instant): Session =
    (1 to n).foldLeft(night(Intake.Drink(ipa, now, Minutes(20)))) { (s, _) =>
      s.copy(intakes = s.intakes :+ SameAgain.next(Some(s), now).get)
    }

  test("quick taps queue drinks back to back") {
    assertEquals(taps(3, at(0)).intakes.map(_.startsAt), Vector(at(0), at(20), at(40), at(60)))
  }

  test("another tap never lowers the peak or brings 'back to ~0' earlier") {
    val now       = at(1)
    val summaries = (0 to 12).map(n => Tonight.of(profile, Some(taps(n, at(0))), now).summary)
    summaries.zip(summaries.tail).zipWithIndex.foreach { case ((a, b), n) =>
      val (pa, pb) = (a.peak.get.bacMgDl, b.peak.get.bacMgDl)
      assert(pb > pa, s"tap ${n + 1}: peak $pa -> $pb mg/dL")
      assert(!b.backToZeroBy.get.isBefore(a.backToZeroBy.get), s"tap ${n + 1}: ${a.backToZeroBy} -> ${b.backToZeroBy}")
    }
  }
}
