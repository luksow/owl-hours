package owlhours.app.logic

import java.time.Duration
import owlhours.domain.*
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

  /** Labels for whole-‰ grid lines in the chosen unit; the chart is a blood curve, so breath users see ‰. */
  def gridLabel(permille: Int, unit: BacUnit): String = unit match
    case BacUnit.PercentBac => s"${Format.number(permille / 10.0)}%"
    case BacUnit.MgPer100ml => s"${permille * 100} mg"
    case _                  => s"$permille‰"

  def impairment(i: Impairment): String = i match
    case Impairment.Sober     => "Sober"
    case Impairment.WarmingUp => "Warming up"
    case Impairment.Tipsy     => "Tipsy"
    case Impairment.Buzzed    => "Buzzed"
    case Impairment.Drunk     => "Drunk"
    case Impairment.Wobbly    => "Wobbly"
    case Impairment.TakeCare  => "Take care"

  def duration(d: Duration): String =
    val (h, m) = (d.toMinutes / 60, d.toMinutes % 60)
    if h == 0 then s"$m min" else if m == 0 then s"$h h" else s"$h h $m min"

  def night(n: NightSummary, unit: BacUnit): String =
    val drinks   = if n.drinks == 1 then "1 drink" else s"${n.drinks} drinks"
    val std      = f"${n.standardDrinks}%.1f"
    val standard = if std == "1.0" then "1.0 standard drink" else s"$std standard drinks"
    List(n.peak.fold("Peak ~0")(p => s"Peak ${peak(p.bacMgDl, unit)}"), drinks, standard, duration(n.duration)).mkString(" · ")

  def unitName(u: BacUnit): String = u match
    case BacUnit.Permille     => "‰ (per mille, blood)"
    case BacUnit.PercentBac   => "% BAC"
    case BacUnit.MgPer100ml   => "mg/100 ml of blood"
    case BacUnit.BreathMgPerL => "mg/L of breath"

  def standardDrinkName(sd: StandardDrink): String = sd match
    case StandardDrink.Grams10 => "10 g — most of Europe, Australia"
    case StandardDrink.Grams8  => "8 g — UK unit"
    case StandardDrink.Grams14 => "14 g — US"

  def intake(i: Intake): String = i match
    case Intake.Drink(s, _, d) => s"${s.name} · ${Format.number(s.volume.unwrap)} ml · ${Format.number(s.abv.unwrap)} % · ${Format.number(d.unwrap)} min"
    case Intake.Water(v, _, d) => s"Water · ${Format.number(v.unwrap)} ml · ${Format.number(d.unwrap)} min"
    case Intake.Meal(k, _)     => s"Food · ${Format.number(k.unwrap)} kcal"
