package owlhours.app.logic

import java.time.Instant
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll
import owlhours.domain.*

class BadgesSuite extends munit.ScalaCheckSuite {
  private val t0               = Instant.parse("2026-10-14T20:00:00Z")
  private def at(min: Long)    = t0.plusSeconds(min * 60)
  private def beer(ml: Double) = DrinkSnapshot(None, "Lager", Millilitres(ml), Abv(5), KcalPerLitre(430), Nil)
  private def drink(min: Long, ml: Double = 568, minutes: Double = 30) =
    Intake.Drink(beer(ml), at(min), Minutes(minutes))
  private def water(min: Long)   = Intake.Water(Millilitres(250), at(min), Minutes(5))
  private def meal(min: Long)    = Intake.Meal(Kcal(700), at(min))
  private def night(is: Intake*) = Session(SessionId("n"), is.map(_.startsAt).minBy(_.toEpochMilli), is.toVector)
  private val sober              = (_: Instant) => 0.0

  test("hydration hero: at least one water for every drink") {
    assert(Badges.of(night(drink(0), water(20), drink(60), water(80)), sober).contains(Badge.HydrationHero))
    assert(!Badges.of(night(drink(0), water(20), drink(60)), sober).contains(Badge.HydrationHero))
  }

  test("fed & steady: a meal at or before the first drink") {
    assert(Badges.of(night(meal(0), drink(30)), sober).contains(Badge.FedAndSteady))
    assert(!Badges.of(night(drink(0), meal(30)), sober).contains(Badge.FedAndSteady))
  }

  test("slow sipper: never more than 15 g of alcohol in any hour, spread over each drink's drinking time") {
    // a UK pint of 5 % lager is 22.4 g: over 90 minutes the busiest hour holds 14.9 g
    assert(Badges.of(night(drink(0, minutes = 90)), sober).contains(Badge.SlowSipper))
    assert(!Badges.of(night(drink(0, minutes = 30)), sober).contains(Badge.SlowSipper))
    // two slow pints back to back keep the pace; two at once do not
    assert(Badges.of(night(drink(0, minutes = 90), drink(90, minutes = 90)), sober).contains(Badge.SlowSipper))
    assert(!Badges.of(night(drink(0, minutes = 90), drink(0, minutes = 90)), sober).contains(Badge.SlowSipper))
  }

  test("early bird: every drink finished while the estimate was under 0.5‰") {
    val n = night(drink(0), drink(60))
    assert(Badges.of(n, _ => 0.4 * BacUnit.MgDlPerPermille).contains(Badge.EarlyBird))
    assert(
      !Badges.of(n, t => if t.isAfter(at(60)) then 0.6 * BacUnit.MgDlPerPermille else 0.0).contains(Badge.EarlyBird)
    )
  }

  test("a night without drinks earns nothing") {
    assertEquals(Badges.of(night(water(0), meal(10)), sober), Nil)
  }

  private val drinks = for {
    start   <- Gen.choose(0L, 300L)
    ml      <- Gen.choose(30.0, 600.0)
    minutes <- Gen.choose(0.0, 90.0)
  } yield drink(start, ml, minutes)
  private val others = Gen.oneOf(Gen.choose(0L, 300L).map(water), Gen.choose(0L, 300L).map(meal))
  private val nights = for {
    ds <- Gen.nonEmptyListOf(drinks)
    os <- Gen.listOf(others)
  } yield night((ds ++ os)*)

  property("logging another drink never earns a badge the night did not have") {
    forAll(nights, drinks) { (n, extra) =>
      val before = Badges.of(n, sober).toSet
      val after  = Badges.of(n.copy(intakes = n.intakes :+ extra), sober).toSet
      assert(after.subsetOf(before), s"gained ${after -- before}")
    }
  }
}
