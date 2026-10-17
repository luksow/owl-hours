package owlhours.app.logic

import java.time.Instant
import owlhours.catalogue.*
import owlhours.domain.*

final case class IntakeEdit(start: Instant, duration: Option[Minutes], volume: Option[Millilitres], abv: Option[Abv], kcal: Option[Kcal])

object Edits {
  /** Proportional: every ingredient scales with the total; concentration (ABV, kcal/L) is unchanged. */
  def rescale(s: DrinkSnapshot, volume: Millilitres): DrinkSnapshot = {
    val k = volume.unwrap / s.volume.unwrap
    // a component can never exceed the drink; clamping also absorbs floating-point overshoot at the 5000 ml limit
    s.copy(volume = volume, components = s.components.map(c =>
      c.copy(volume = Millilitres.from(math.min(c.volume.unwrap * k, volume.unwrap)).getOrElse(volume))),
      meltWater = s.meltWater.flatMap(m => Millilitres.from(m.unwrap * k).toOption))
  }

  /** Scales the whole drink so that its poured volume (what the person sees) is `poured`; melt water follows. */
  def rescalePoured(s: DrinkSnapshot, poured: Millilitres): DrinkSnapshot =
    Millilitres.from(s.volume.unwrap * poured.unwrap / s.poured.unwrap).fold(_ => s, rescale(s, _))

  /** How far a logged drink's ABV may be edited; `None` means it is fixed (a product with a set ABV, or a cocktail). */
  def abvRange(entry: Option[Entry]): Option[(Abv, Abv)] = entry match {
    case Some(s: Style)                      => Some((s.abvMin, s.abvMax))
    case Some(p: Product) if p.abvAdjustable => Some((Abv(0), Abv(100)))
    case Some(_)                             => None
    case None                                => Some((Abv(0), Abv(100)))
  }

  def checkAbv(abv: Abv, range: (Abv, Abv), name: String): Either[String, Abv] =
    if abv.unwrap < range._1.unwrap || abv.unwrap > range._2.unwrap then
      Left(s"ABV for $name must be between ${Format.number(range._1.unwrap)} and ${Format.number(range._2.unwrap)} %, got ${Format.number(abv.unwrap)}")
    else Right(abv)

  def withAbv(s: DrinkSnapshot, abv: Abv): Either[String, DrinkSnapshot] =
    if s.components.nonEmpty then Left("The ABV of a mixed drink follows its ingredients")
    else
      KcalPerLitre
        .from(Energy.withAbv(s.kcalPerLitre.unwrap, s.abv.unwrap, abv.unwrap))
        .map(k => s.copy(abv = abv, kcalPerLitre = k))

  def apply(i: Intake, e: IntakeEdit): Either[String, Intake] = i match {
    case Intake.Drink(snapshot, _, duration) =>
      // a drink's edited volume is the poured volume, as Log shows it
      val scaled = e.volume.fold(snapshot)(rescalePoured(snapshot, _))
      e.abv.fold(Right(scaled))(withAbv(scaled, _)).map(s => Intake.Drink(s, e.start, e.duration.getOrElse(duration)))
    case Intake.Water(volume, _, duration) =>
      Right(Intake.Water(e.volume.getOrElse(volume), e.start, e.duration.getOrElse(duration)))
    case Intake.Meal(kcal, _) =>
      Right(Intake.Meal(e.kcal.getOrElse(kcal), e.start))
  }

  def notInFuture(start: Instant, now: Instant): Either[String, Instant] =
    // A time later today is almost always last night with today's (prefilled) date, e.g. 23:50 picked at 00:20.
    if !start.isAfter(now.plusSeconds(60)) then Right(start)
    else if start.isBefore(now.plusSeconds(24 * 3600)) then Left("That's later than now — for last night, pick yesterday's date")
    else Left("That time is in the future")
}
