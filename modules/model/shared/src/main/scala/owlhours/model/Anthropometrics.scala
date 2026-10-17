package owlhours.model

/** Model inputs in model units: sex 1 = male / 0 = female, kg, m, years. */
final case class Anthropometrics(sex: Double, weightKg: Double, heightM: Double, ageYears: Double)

/** Fixed compartment volumes in dL. Tissue volume is state-dependent and computed in the RHS. */
final case class Volumes(
    liver: Double,
    blood: Double,
    bloodPeripheral: Double,
    bloodCentral: Double,
    plasmaPeripheral: Double,
    totalBodyWater: Double
)

object Volumes {
  def of(a: Anthropometrics, p: Params): Volumes = {
    val s = a.sex
    val w = a.weightKg
    val h = a.heightM
    val liver = (18.51 * w + 191.8) / 100 // Vauthey, doi:10.1053/jlts.2002.31654
    val blood = // Nadler's equation
      ((1 - s) * (0.3561 * h * h * h + 0.03308 * w + 0.1833) + s * (0.3669 * h * h * h + 0.03219 * w + 0.6041)) * 10
    val tbw = // Watson, doi:10.15288/jsa.1981.42.547
      ((1 - s) * ((-2.097 + 0.1069 * (h * 100) + 0.2466 * w) / 0.84) +
        s * ((2.447 - 0.09516 * a.ageYears + 0.1074 * (h * 100) + 0.3362 * w) / 0.84)) * 10
    Volumes(
      liver = liver,
      blood = blood,
      bloodPeripheral = 0.85 * blood,
      bloodCentral = 0.15 * blood,
      plasmaPeripheral = p.kBloodPlasmaRatio * 0.85 * blood,
      totalBodyWater = tbw
    )
  }
}
