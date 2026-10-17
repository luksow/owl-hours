package owlhours.model

/** Best-fit parameters of alcohol_model_2 (cost 642.74446), copied from the model file. MIT, see reference/LICENSE-alcohol-model.md. */
final case class Params(
    vmaxGastric: Double,
    kmGastric: Double,
    kKcalScaling: Double,
    kmKcal: Double,
    nKcal: Double,
    kPoolIn: Double,
    kPoolOut: Double,
    vmaxADHSto: Double,
    kmADHSto: Double,
    kEtOHUptake: Double,
    kEqualize: Double,
    kEqualizeLiver: Double,
    vmaxADH1: Double,
    vmaxCYP2E1: Double,
    kmADH1: Double,
    kmCYP2E1: Double,
    kFoodClearance: Double,
    kAcetate: Double,
    kBloodPlasmaRatio: Double,
    kUrineWater: Double,
    kBasalVasopressin: Double,
    kVasopressinBAC: Double,
    kClearanceVasopressin: Double,
    kKidneyPermeability: Double,
    vmaxEtG: Double,
    kmEtG: Double,
    vmaxEtS: Double,
    kmEtS: Double,
    kEtGUrine: Double,
    kEtSUrine: Double,
    kPEth: Double,
    kPEthClearance: Double,
    kPEthBind: Double,
    kPEthRelease: Double
)

object Params {
  val published: Params = Params(
    vmaxGastric = 0.10028847533601028,
    kmGastric = 0.8115471295053232,
    kKcalScaling = 0.026009077044725923,
    kmKcal = 7219.025648168511,
    nKcal = 2.9350431505014307,
    kPoolIn = 0.049215056963481905,
    kPoolOut = 0.00011026963373147828,
    vmaxADHSto = 383.0316652068957,
    kmADHSto = 1750.8565753331234,
    kEtOHUptake = 0.05085931578651339,
    kEqualize = 3.364285096106452,
    kEqualizeLiver = 0.5664934932515018,
    vmaxADH1 = 7.490115599723829,
    vmaxCYP2E1 = 0.7715882956131286,
    kmADH1 = 2.13180346563429,
    kmCYP2E1 = 45.98540429542478,
    kFoodClearance = 0.1350242774593947,
    kAcetate = 1.1650870599405063,
    kBloodPlasmaRatio = 0.6871729866586396,
    kUrineWater = 5.961711956546037,
    kBasalVasopressin = 0.3849587409310818,
    kVasopressinBAC = 74740.43401768291,
    kClearanceVasopressin = 0.0006626630440471179,
    kKidneyPermeability = 2.5215624280668018e-05,
    vmaxEtG = 0.6461457040480787,
    kmEtG = 29854.322456892394,
    vmaxEtS = 0.011876595138349037,
    kmEtS = 414.40740948670106,
    kEtGUrine = 0.0024879684263205454,
    kEtSUrine = 0.014152433882270748,
    kPEth = 0.13959486810293995,
    kPEthClearance = 0.001460712263112933,
    kPEthBind = 0.07805739912357477,
    kPEthRelease = 0.002851044320061577
  )

  /*
   * Person-to-person variation corners. Elimination rates in healthy adults vary roughly ±20–25 % around the
   * mean (Jones, Forensic Sci Int 2010;200:1–20); absorption varies more (Jones, WIREs Forensic Sci 2019;1:e1340).
   * Shown in the app as "typical person-to-person variation", never as a confidence interval.
   */
  def slowMetaboliser(p: Params): Params =
    p.copy(
      vmaxADH1 = p.vmaxADH1 * 0.8,
      vmaxCYP2E1 = p.vmaxCYP2E1 * 0.8,
      kEtOHUptake = p.kEtOHUptake * 1.3,
      vmaxGastric = p.vmaxGastric * 1.3
    )

  def fastMetaboliser(p: Params): Params =
    p.copy(
      vmaxADH1 = p.vmaxADH1 * 1.2,
      vmaxCYP2E1 = p.vmaxCYP2E1 * 1.2,
      kEtOHUptake = p.kEtOHUptake * 0.7,
      vmaxGastric = p.vmaxGastric * 0.7
    )
}
