/*
 * Port of alcohol_model_2 by William Lövfors, Christian Simonsson and Henrik Podéus
 * (https://github.com/willov/alcohol_app, MIT licence, see reference/LICENSE-alcohol-model.md).
 * Equations follow reference/alcohol_model_2.txt line by line; keep it that way so the two can be diffed.
 */
package owlhours.model

final case class Inputs(etohConcPercent: Double, volRateLPerMin: Double, kcalPerLitre: Double)
object Inputs {
  val zero: Inputs = Inputs(0, 0, 0)
}

/** State vector layout; same order as SUND's state_names. */
object State {
  val VolStomach          = 0
  val KcalLiquid          = 1
  val KcalRemain          = 2
  val MaxKcalSolid        = 3
  val KcalSolid           = 4
  val EtOHPool            = 5
  val StomachConc         = 6
  val MassEtOHIntestines  = 7
  val BloodConcCentral    = 8
  val BloodConcPeripheral = 9
  val TissueConc          = 10
  val LiverConc           = 11
  val LiverEtG            = 12
  val PeripheralEtG       = 13
  val LiverEtS            = 14
  val PeripheralEtS       = 15
  val PlasmaAcetate       = 16
  val TissueExtraWater    = 17
  val Vasopressin         = 18
  val UrineMassEtOH       = 19
  val UrineVolume         = 20
  val UrineMassEtG        = 21
  val UrineMassEtS        = 22
  val PEth                = 23
  val PEthBound           = 24
  val TimeElapsed         = 25
  val Size                = 26

  def initial(p: Params): Array[Double] = {
    val y = new Array[Double](Size)
    y(VolStomach) = AlcoholModel2.SsVol
    y(Vasopressin) = p.kBasalVasopressin / p.kClearanceVasopressin
    y(UrineVolume) = AlcoholModel2.SsUrineVol
    y
  }
}

object AlcoholModel2 {
  val SsVol: Double      = 0.001 // L
  val SsUrineVol: Double = 0.05  // dL

  /** Breath alcohol in mg/L from central blood concentration (mg/dL), as defined in the model's features. */
  def bracMgL(bloodConcCentral: Double): Double = (10.0 / 2.1) * (0.840 * (bloodConcCentral / 1000) + 0.00367)

  final class Rhs(p: Params, v: Volumes) extends Solver.Rhs {
    var inputs: Inputs = Inputs.zero

    def apply(t: Double, y: Array[Double], dy: Array[Double]): Unit = {
      import State.*
      val volS    = y(VolStomach)
      val kcalLiq = y(KcalLiquid)
      val kcalRem = y(KcalRemain)
      val maxKS   = y(MaxKcalSolid)
      val kS      = y(KcalSolid)
      val pool    = y(EtOHPool)
      val stC     = y(StomachConc)
      val mInt    = y(MassEtOHIntestines)
      val bc      = y(BloodConcCentral)
      val bp      = y(BloodConcPeripheral)
      val tis     = y(TissueConc)
      val liv     = y(LiverConc)
      val lEtG    = y(LiverEtG)
      val pEtG    = y(PeripheralEtG)
      val lEtS    = y(LiverEtS)
      val pEtS    = y(PeripheralEtS)
      val ac      = y(PlasmaAcetate)
      val tew     = y(TissueExtraWater)
      val vaso    = y(Vasopressin)
      val uE      = y(UrineMassEtOH)
      val uV      = y(UrineVolume)
      val peth    = y(PEth)
      val pethB   = y(PEthBound)
      val tel     = y(TimeElapsed)

      val volRate    = inputs.volRateLPerMin
      val kcalPerVol = inputs.kcalPerLitre

      val vTissue = v.totalBodyWater - v.blood - v.liver + tew

      // gastric emptying
      val xk          = kcalLiq / p.kKcalScaling
      val xkn         = math.pow(xk, p.nKcal)
      val kcalEffect  = 1 - xkn / (xkn + math.pow(p.kmKcal, p.nKcal))
      val rVolEmpty   = p.vmaxGastric * ((volS - SsVol) / ((volS - SsVol) + p.kmGastric)) * kcalEffect
      val stomachKcal = kcalRem / volS

      // food
      val te         = 0.010 * math.max(0, tel)
      val rDigestion = maxKS * 1.88 * 0.010 * math.pow(te, 0.86) * math.exp(-math.pow(te, 1.86))
      val kcalSolidV = kS / (4 * 1000)
      val gtVol      = if volS > 2 * SsVol then 1.0 else 0.0
      val rPoolIn    = gtVol * math.max(0, (stC - pool / (1.0 / 4000 + kcalSolidV)) * p.kPoolIn)
      val rPoolOut   = gtVol * pool * p.kPoolOut

      // stomach ethanol
      val concDrink = inputs.etohConcPercent * 789.1 * 10
      val rDrink    = (concDrink - stC) * (volRate / volS)
      val rStADH    = p.vmaxADHSto * (stC / (stC + p.kmADHSto))

      // uptake and distribution
      val rUptake = mInt * p.kEtOHUptake
      val rCirc   = p.kEqualize * (bc - bp)
      val rLiver  = p.kEqualizeLiver * (bp - liv)
      val rTisP   = bp - tis

      // elimination and metabolites
      val vADH1   = p.vmaxADH1 * (liv / (p.kmADH1 + liv))
      val vCYP    = p.vmaxCYP2E1 * liv / (p.kmCYP2E1 + liv)
      val rElim   = (vADH1 + vCYP) * (1 + p.kFoodClearance * (if kS > 1e-3 then 1.0 else 0.0))
      val rEtG    = p.vmaxEtG * (liv / (liv + p.kmEtG))
      val rEtS    = p.vmaxEtS * (liv / (liv + p.kmEtS))
      val rEtGLP  = lEtG - pEtG
      val rEtSLP  = lEtS - pEtS
      val rAcetate = ac * p.kAcetate

      // vasopressin and urine
      val rVasoIn       = p.kBasalVasopressin / (1 + p.kVasopressinBAC * bp)
      val rVasoCl       = vaso * p.kClearanceVasopressin
      val rUrineMinVol  = 0.01
      val rUrineMinMass = rUrineMinVol * bp
      val rTissueUrine  = (tew / vaso) * p.kUrineWater
      val plasmaConc    = (bp * v.bloodPeripheral) / v.plasmaPeripheral
      val rUrine        = rTissueUrine * plasmaConc
      val rUrineReturn  = p.kKidneyPermeability * vaso * ((uE / uV) - plasmaConc) * uV
      val rEtGUrine     = p.kEtGUrine * pEtG
      val rEtSUrine     = p.kEtSUrine * pEtS

      // PEth
      val rPEth      = p.kPEth * bp
      val rPEthCl    = p.kPEthClearance * peth
      val rPEthBound = p.kPEthBind * peth
      val rPEthRel   = if pethB - peth > 0 then p.kPEthRelease * (pethB - peth) else 0.0

      dy(VolStomach) = volRate - rVolEmpty
      dy(KcalLiquid) = volRate * kcalPerVol
      dy(KcalRemain) = volRate * kcalPerVol - rVolEmpty * stomachKcal
      dy(MaxKcalSolid) = 0
      dy(KcalSolid) = -rDigestion
      dy(EtOHPool) = rPoolIn * volS - rPoolOut
      dy(StomachConc) = rDrink - rPoolIn + rPoolOut / volS - rStADH
      dy(MassEtOHIntestines) = rVolEmpty * stC - rUptake
      dy(BloodConcCentral) = rUptake / v.bloodCentral - rCirc
      dy(BloodConcPeripheral) = rCirc * (v.bloodCentral / v.bloodPeripheral) - rTisP - rLiver +
        (rUrineReturn - rUrineMinMass - rUrine) / v.bloodPeripheral
      dy(TissueConc) = rTisP * (v.bloodPeripheral / vTissue)
      dy(LiverConc) = rLiver * (v.bloodPeripheral / v.liver) - rElim - rEtG - rEtS
      dy(LiverEtG) = rEtG - rEtGLP
      dy(PeripheralEtG) = rEtGLP * (v.liver / v.bloodPeripheral) - rEtGUrine
      dy(LiverEtS) = rEtS - rEtSLP
      dy(PeripheralEtS) = rEtSLP * (v.liver / v.bloodPeripheral) - rEtSUrine
      dy(PlasmaAcetate) = rElim - rAcetate
      dy(TissueExtraWater) = rVolEmpty * 10 - rTissueUrine
      dy(Vasopressin) = rVasoIn - rVasoCl
      dy(UrineMassEtOH) = rUrineMinMass + rUrine - rUrineReturn
      dy(UrineVolume) = rUrineMinVol + rTissueUrine
      dy(UrineMassEtG) = rEtGUrine * v.bloodPeripheral
      dy(UrineMassEtS) = rEtSUrine * v.bloodPeripheral
      dy(PEth) = rPEth - rPEthBound + rPEthRel - rPEthCl
      dy(PEthBound) = rPEthBound - rPEthRel
      dy(TimeElapsed) = 1
    }
  }
}
