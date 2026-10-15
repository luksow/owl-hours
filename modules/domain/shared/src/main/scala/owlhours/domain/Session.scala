package owlhours.domain

import java.time.Instant

/** One night. It is open or closed by rule (see app logic `Sessions`), not by a stored flag. */
final case class Session(id: SessionId, startedAt: Instant, intakes: Vector[Intake]):
  def lastDrink: Option[Intake.Drink] = intakes.collect { case d: Intake.Drink => d }.maxByOption(_.start.toEpochMilli)

final case class Settings(bacUnit: BacUnit)

object Settings:
  val default: Settings = Settings(BacUnit.Permille)
