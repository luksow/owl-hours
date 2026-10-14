package owlhours.domain

enum BacUnit:
  case Permille, PercentBac, MgPer100ml, BreathMgPerL

  /** Converts model outputs (blood mg/dL, breath mg/L) into this display unit. */
  def fromModel(bacMgDl: Double, bracMgL: Double): Double = this match
    case Permille     => bacMgDl / BacUnit.MgDlPerPermille
    case PercentBac   => bacMgDl / 1000.0
    case MgPer100ml   => bacMgDl
    case BreathMgPerL => bracMgL

  def symbol: String = this match
    case Permille     => "‰"
    case PercentBac   => "%"
    case MgPer100ml   => "mg/100 ml"
    case BreathMgPerL => "mg/L"

object BacUnit:
  /** The model's own conversion: ‰ = mg/dL ÷ 105. */
  val MgDlPerPermille: Double = 105.0
