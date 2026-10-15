package owlhours.app.logic

import owlhours.domain.BacUnit
import owlhours.model.Impairment

object Display:
  def bac(bacMgDl: Double, bracMgL: Double, unit: BacUnit): String =
    val v = unit.fromModel(bacMgDl, bracMgL)
    unit match
      case BacUnit.Permille     => f"$v%.2f‰"
      case BacUnit.PercentBac   => f"$v%.3f%%"
      case BacUnit.MgPer100ml   => f"$v%.0f mg/100 ml"
      case BacUnit.BreathMgPerL => f"$v%.2f mg/L"

  /** Peaks are blood figures; with the breath unit they are shown in ‰, as on Tonight. */
  def peak(bacMgDl: Double, unit: BacUnit): String = bac(bacMgDl, 0, if unit == BacUnit.BreathMgPerL then BacUnit.Permille else unit)

  def impairment(i: Impairment): String = i match
    case Impairment.Sober     => "Sober"
    case Impairment.WarmingUp => "Warming up"
    case Impairment.Tipsy     => "Tipsy"
    case Impairment.Buzzed    => "Buzzed"
    case Impairment.Drunk     => "Drunk"
    case Impairment.Wobbly    => "Wobbly"
    case Impairment.TakeCare  => "Take care"
