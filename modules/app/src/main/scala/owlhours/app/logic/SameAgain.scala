package owlhours.app.logic

import java.time.Instant
import owlhours.domain.*

/**
 * "Same again" lines the drink up behind any drink still going, so quick taps mean drinks in a row. Drinks logged on
 * top of each other pour litres into the stomach at once, where the model's calorie brake stops gastric emptying and
 * the estimate falls as more is drunk.
 */
object SameAgain {
  def next(session: Option[Session], now: Instant): Option[Intake] =
    session.flatMap { s =>
      val drinks = s.intakes.collect { case d: Intake.Drink => d }
      s.lastDrink.map { last =>
        val busyUntil = drinks.map(Sessions.endOf).maxBy(_.toEpochMilli)
        Intake.Drink(last.snapshot, if busyUntil.isAfter(now) then busyUntil else now, last.duration)
      }
    }
}
