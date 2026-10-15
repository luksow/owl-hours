package owlhours.app.logic

import java.time.Instant
import owlhours.catalogue.Energy
import owlhours.domain.*

final case class IntakeEdit(start: Instant, duration: Option[Minutes], volume: Option[Millilitres], abv: Option[Abv], kcal: Option[Kcal])

object Edits:
  /** Proportional: every ingredient scales with the total; concentration (ABV, kcal/L) is unchanged. */
  def rescale(s: DrinkSnapshot, volume: Millilitres): DrinkSnapshot =
    val k = volume.unwrap / s.volume.unwrap
    s.copy(volume = volume, components = s.components.map(c => c.copy(volume = Millilitres(c.volume.unwrap * k))))

  def withAbv(s: DrinkSnapshot, abv: Abv): Either[String, DrinkSnapshot] =
    if s.components.nonEmpty then Left("The ABV of a mixed drink follows its ingredients")
    else
      KcalPerLitre
        .from(Energy.withAbv(s.kcalPerLitre.unwrap, s.abv.unwrap, abv.unwrap))
        .map(k => s.copy(abv = abv, kcalPerLitre = k))

  def apply(i: Intake, e: IntakeEdit): Either[String, Intake] = i match
    case Intake.Drink(snapshot, _, duration) =>
      val scaled = e.volume.fold(snapshot)(rescale(snapshot, _))
      e.abv.fold(Right(scaled))(withAbv(scaled, _)).map(s => Intake.Drink(s, e.start, e.duration.getOrElse(duration)))
    case Intake.Water(volume, _, duration) =>
      Right(Intake.Water(e.volume.getOrElse(volume), e.start, e.duration.getOrElse(duration)))
    case Intake.Meal(kcal, _) =>
      Right(Intake.Meal(e.kcal.getOrElse(kcal), e.start))

  def notInFuture(start: Instant, now: Instant): Either[String, Instant] =
    if start.isAfter(now.plusSeconds(60)) then Left("That time is in the future") else Right(start)
