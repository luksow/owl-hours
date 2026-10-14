package owlhours.domain

import java.time.Instant

final case class ComponentLine(name: String, volume: Millilitres, abv: Abv)

/** A drink fully resolved at log time; later catalogue edits never change it. */
final case class DrinkSnapshot(
    catalogueId: Option[CatalogueId],
    name: String,
    volume: Millilitres,
    abv: Abv,
    kcalPerLitre: KcalPerLitre,
    components: List[ComponentLine]
):
  def ethanolGrams: Double = volume.unwrap * abv.unwrap / 100.0 * DrinkSnapshot.EthanolDensity

object DrinkSnapshot:
  /** g/ml at 20 °C. */
  val EthanolDensity: Double = 0.789

enum Intake:
  case Drink(snapshot: DrinkSnapshot, start: Instant, duration: Minutes)
  case Water(volume: Millilitres, start: Instant, duration: Minutes)
  case Meal(kcal: Kcal, at: Instant)

  /** Named differently from the `start` fields so the cases do not override it. */
  def startsAt: Instant = this match
    case d: Drink => d.start
    case w: Water => w.start
    case m: Meal  => m.at
