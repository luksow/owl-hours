package owlhours.domain

import java.time.Instant

/**
 * One night. It is open or closed by rule (see app logic `Sessions`), not by a stored flag; `endedAt` records only
 * that the person ended it by hand, and regrouping keeps it.
 */
final case class Session(id: SessionId, startedAt: Instant, intakes: Vector[Intake], endedAt: Option[Instant] = None):
  def lastDrink: Option[Intake.Drink] = intakes.collect { case d: Intake.Drink => d }.maxByOption(_.start.toEpochMilli)

/** How many grams of ethanol one standard drink holds where the person lives. */
enum StandardDrink(val grams: Double):
  case Grams10 extends StandardDrink(10)
  case Grams8  extends StandardDrink(8)
  case Grams14 extends StandardDrink(14)

object StandardDrink:
  val default: StandardDrink = Grams10

final case class Settings(bacUnit: BacUnit, standardDrink: StandardDrink = StandardDrink.default)

object Settings:
  val default: Settings = Settings(BacUnit.Permille)
