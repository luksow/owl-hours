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
    components: List[ComponentLine],
    /** Ice melt a mixed drink gains (catalogue `Method`), included in `volume`; absent in data saved before Plan 8. */
    meltWater: Option[Millilitres] = None
) {
  /** What was poured: the volume the person sees and edits; melt water is added on top for the model (spec §9). */
  def poured: Millilitres = meltWater.fold(volume)(m => Millilitres.from(volume.unwrap - m.unwrap).getOrElse(volume))

  def ethanolGrams: Double = volume.unwrap * abv.unwrap / 100.0 * DrinkSnapshot.EthanolDensity

  /** Energy other than the ethanol's own (sugars, dextrins…), kcal/L: what the model's gastric brake responds to. */
  def nonAlcoholKcalPerLitre: Double =
    math.max(0.0, kcalPerLitre.unwrap - abv.unwrap * 10 * DrinkSnapshot.EthanolDensity * DrinkSnapshot.EthanolKcalPerGram)
}

object DrinkSnapshot {
  /** g/ml at 20 °C. */
  val EthanolDensity: Double = 0.789

  /** EU Reg. 1169/2011 Annex XIV; spirit labels use this factor (40 % → 221 kcal/100 ml). */
  val EthanolKcalPerGram: Double = 7.0
}

enum Intake {
  case Drink(snapshot: DrinkSnapshot, start: Instant, duration: Minutes)
  case Water(volume: Millilitres, start: Instant, duration: Minutes)
  case Meal(kcal: Kcal, at: Instant)

  /** Named differently from the `start` fields so the cases do not override it. */
  def startsAt: Instant = this match {
    case d: Drink => d.start
    case w: Water => w.start
    case m: Meal  => m.at
  }
}
