package owlhours.app.logic

import java.time.Instant
import owlhours.domain.*

/** Healthy-behaviour badges (spec §7.3). None depends on drinking more; BadgesSuite pins that as a property. */
enum Badge(val title: String, val why: String) {
  case HydrationHero extends Badge("Hydration hero", "At least one water for every drink.")
  case FedAndSteady  extends Badge("Fed & steady", "Ate before the first drink.")
  case SlowSipper    extends Badge("Slow sipper", "Never more than 15 g of alcohol in any hour.")
  case EarlyBird     extends Badge("Early bird", "Every drink was finished while the estimate was under 0.5‰.")
}

object Badges {
  val SlowSipperGramsPerHour: Double = 15.0
  val EarlyBirdMgDl: Double          = 0.5 * BacUnit.MgDlPerPermille
  private val HourMs                 = 3600000.0

  def of(s: Session, bacMgDlAt: Instant => Double): List[Badge] = {
    val drinks = s.intakes.collect { case d: Intake.Drink => d }
    if drinks.isEmpty then Nil
    else {
      val waters    = s.intakes.count { case _: Intake.Water => true; case _ => false }
      val firstSip  = drinks.map(_.start).minBy(_.toEpochMilli)
      val fed       = s.intakes.exists { case Intake.Meal(_, at) => !at.isAfter(firstSip); case _ => false }
      val slow      = busiestHourGrams(drinks) <= SlowSipperGramsPerHour
      val early     = drinks.forall(d => bacMgDlAt(Sessions.endOf(d)) < EarlyBirdMgDl)
      List(
        Option.when(waters >= drinks.size)(Badge.HydrationHero),
        Option.when(fed)(Badge.FedAndSteady),
        Option.when(slow)(Badge.SlowSipper),
        Option.when(early)(Badge.EarlyBird)
      ).flatten
    }
  }

  /**
   * The most ethanol drunk in any 60-minute window, each drink spread evenly over its drinking time (a zero-minute
   * shot counts as half a minute, as in the model). The maximum lies on a window starting at a drink's start or
   * ending at a drink's end.
   */
  private[logic] def busiestHourGrams(drinks: Vector[Intake.Drink]): Double = {
    def span(d: Intake.Drink) = {
      val a = d.start.toEpochMilli.toDouble
      (a, a + math.max(d.duration.unwrap, 0.5) * 60000)
    }
    val starts = drinks.flatMap { d => val (a, b) = span(d); Vector(a, b - HourMs) }
    starts.map { w0 =>
      drinks.map { d =>
        val (a, b) = span(d)
        d.snapshot.ethanolGrams * math.max(0.0, math.min(b, w0 + HourMs) - math.max(a, w0)) / (b - a)
      }.sum
    }.max
  }
}
