package owlhours.catalogue

import owlhours.domain.DrinkSnapshot

object Energy:
  val EthanolKcalPerGram: Double = DrinkSnapshot.EthanolKcalPerGram
  val CarbKcalPerGram: Double    = 4.0

  def ethanolKcalPerLitre(abv: Double): Double = abv * 10 * DrinkSnapshot.EthanolDensity * EthanolKcalPerGram

  /** Carbs here means all non-ethanol energy (sugars, dextrins, protein, glycerol) in g/L. */
  def styleKcalPerLitre(abv: Double, carbsPerLitre: Double): Double =
    ethanolKcalPerLitre(abv) + carbsPerLitre * CarbKcalPerGram

  /** Same product at a different ABV: keep the non-alcohol calories, swap the alcohol part. */
  def withAbv(kcalPerLitre: Double, fromAbv: Double, toAbv: Double): Double =
    math.max(0.0, kcalPerLitre - ethanolKcalPerLitre(fromAbv)) + ethanolKcalPerLitre(toAbv)
