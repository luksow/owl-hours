package owlhours.domain

import java.time.Instant

/**
 * One night. It is open or closed by rule (see app logic `Sessions`), not by a stored flag; `endedAt` records only
 * that the person ended it by hand, and regrouping keeps it.
 */
final case class Session(id: SessionId, startedAt: Instant, intakes: Vector[Intake], endedAt: Option[Instant] = None):
  def lastDrink: Option[Intake.Drink] = intakes.collect { case d: Intake.Drink => d }.maxByOption(_.start.toEpochMilli)

final case class Settings(bacUnit: BacUnit)

object Settings:
  val default: Settings = Settings(BacUnit.Permille)
