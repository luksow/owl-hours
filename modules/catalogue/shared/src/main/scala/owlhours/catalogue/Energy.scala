package owlhours.catalogue

import owlhours.domain.DrinkSnapshot

object Energy:
  /** EU Reg. 1169/2011 Annex XIV; spirit labels use this factor (40 % → 221 kcal/100 ml). */
  val EthanolKcalPerGram: Double = 7.0
  val CarbKcalPerGram: Double    = 4.0

  def ethanolKcalPerLitre(abv: Double): Double = abv * 10 * DrinkSnapshot.EthanolDensity * EthanolKcalPerGram

  /** Carbs here means all non-ethanol energy (sugars, dextrins, protein, glycerol) in g/L. */
  def styleKcalPerLitre(abv: Double, carbsPerLitre: Double): Double =
    ethanolKcalPerLitre(abv) + carbsPerLitre * CarbKcalPerGram

  /** Same product at a different ABV: keep the non-alcohol calories, swap the alcohol part. */
  def withAbv(kcalPerLitre: Double, fromAbv: Double, toAbv: Double): Double =
    math.max(0.0, kcalPerLitre - ethanolKcalPerLitre(fromAbv)) + ethanolKcalPerLitre(toAbv)
