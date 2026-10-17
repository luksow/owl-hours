package owlhours.app.logic

import java.time.{Duration, Instant}
import owlhours.domain.*
import owlhours.model.{Impairment, Peak, Trend}

object Display {
  def bac(bacMgDl: Double, bracMgL: Double, unit: BacUnit): String = {
    val (number, suffix) = bacParts(bacMgDl, bracMgL, unit)
    number + suffix
  }

  /** The number and its unit apart, so Tonight can set the unit smaller (identity prototype, section 06). */
  def bacParts(bacMgDl: Double, bracMgL: Double, unit: BacUnit): (String, String) = {
    val v = unit.fromModel(bacMgDl, bracMgL)
    unit match {
      case BacUnit.Permille     => (f"$v%.2f", "‰")
      case BacUnit.PercentBac   => (f"$v%.3f", "%")
      case BacUnit.MgPer100ml   => (f"$v%.0f", " mg/100 ml")
      case BacUnit.BreathMgPerL => (f"$v%.2f", " mg/L")
    }
  }

  /** The line under the number: the trend, and while rising towards a peak still ahead, its time. */
  def trendLine(trend: Trend, peak: Option[Peak], now: Instant, hhmm: Instant => String): String = trend match {
    case Trend.Rising =>
      peak.filter(_.at.isAfter(now)).fold("↗ Rising")(p => s"↗ Rising · peak ${hhmm(p.at)}")
    case Trend.Falling => "↘ Falling"
    case Trend.Steady  => "→ Steady"
  }

  /** Peaks are blood figures; with the breath unit they are shown in ‰, as on Tonight. */
  def peak(bacMgDl: Double, unit: BacUnit): String = bac(bacMgDl, 0, if unit == BacUnit.BreathMgPerL then BacUnit.Permille else unit)

  /** The back-to chip (spec §4): the time, or the end of the window when the estimate is still above ~0 there. A time
    * more than 16 hours away names its day, so "by 21:35" is never read as tonight when it means tomorrow evening. */
  def backTo(t: Tonight, now: Instant, hhmm: Instant => String, weekday: Instant => String): Option[String] = {
    def at(i: Instant) = if i.isAfter(now.plusSeconds(16 * 3600)) then s"${weekday(i)} ${hhmm(i)}" else hhmm(i)
    t.summary.backToZeroBy match {
      case Some(b)          => Some(s"Back to ~0 by ${at(b)}")
      case None if t.capped => Some(s"Back to ~0 after ${at(t.viewTo)}+")
      case None             => None
    }
  }

  /** The chart's grid as (mg/dL, label), in round steps of the chosen unit (spec §3): every 0.5 ‰ (1 ‰ above 3 ‰), or
    * every 50 mg/100 ml = 0.05 % (100 mg = 0.1 % above about 3 ‰). Breath users see blood ‰: the chart is a blood curve. */
  def gridLines(unit: BacUnit, topMgDl: Double): List[(Double, String)] = unit match {
    case BacUnit.MgPer100ml | BacUnit.PercentBac =>
      val step = if topMgDl > 3 * BacUnit.MgDlPerPermille then 100.0 else 50.0
      Iterator.iterate(0.0)(_ + step).takeWhile(_ <= topMgDl + 1e-9).map { v =>
        v -> (if v == 0 then "0" else if unit == BacUnit.MgPer100ml then f"$v%.0f mg" else f"${v / 1000}%.2f%%")
      }.toList
    case _ =>
      ChartSvg.gridSteps(topMgDl / BacUnit.MgDlPerPermille).map(k => k * BacUnit.MgDlPerPermille -> gridLabel(k, BacUnit.Permille))
  }

  /** Grid labels in the chosen unit, at half-‰ steps; the chart is a blood curve, so breath users see ‰. */
  def gridLabel(permille: Double, unit: BacUnit): String =
    if permille == 0 then "0"
    else unit match {
      case BacUnit.PercentBac => s"${Format.number(permille / 10.0)}%"
      case BacUnit.MgPer100ml => s"${Format.number(permille * 100)} mg"
      case _                  => s"${Format.number(permille)}‰"
    }

  def impairment(i: Impairment): String = i match {
    case Impairment.Sober     => "Sober"
    case Impairment.WarmingUp => "Warming up"
    case Impairment.Tipsy     => "Tipsy"
    case Impairment.Buzzed    => "Buzzed"
    case Impairment.Drunk     => "Drunk"
    case Impairment.Wobbly    => "Wobbly"
    case Impairment.TakeCare  => "Take care"
  }

  def duration(d: Duration): String = {
    val (h, m) = (d.toMinutes / 60, d.toMinutes % 60)
    if h == 0 then s"$m min" else if m == 0 then s"$h h" else s"$h h $m min"
  }

  def night(n: NightSummary, unit: BacUnit): String = {
    val drinks   = if n.drinks == 1 then "1 drink" else s"${n.drinks} drinks"
    val std      = f"${n.standardDrinks}%.1f"
    val standard = if std == "1.0" then "1.0 standard drink" else s"$std standard drinks"
    List(n.peak.fold("Peak ~0")(p => s"Peak ${peak(p.bacMgDl, unit)}"), drinks, standard, duration(n.duration)).mkString(" · ")
  }

  def unitName(u: BacUnit): String = u match {
    case BacUnit.Permille     => "‰ (per mille, blood)"
    case BacUnit.PercentBac   => "% BAC"
    case BacUnit.MgPer100ml   => "mg/100 ml of blood"
    case BacUnit.BreathMgPerL => "mg/L of breath"
  }

  def standardDrinkName(sd: StandardDrink): String = sd match {
    case StandardDrink.Grams10 => "10 g — most of Europe, Australia"
    case StandardDrink.Grams8  => "8 g — UK unit"
    case StandardDrink.Grams14 => "14 g — US"
  }

  /** The read-only note under a cocktail's amount (spec §9); `method` reads like "built over ice". */
  def meltNote(s: DrinkSnapshot, method: String): Option[String] =
    s.meltWater.map(m => s"+ ${Format.number(math.round(m.unwrap * 10) / 10.0)} ml melt water ($method), included in the strength")

  def intake(i: Intake): String = i match {
    case Intake.Drink(s, _, d) => s"${s.name} · ${Format.number(math.round(s.poured.unwrap * 10) / 10.0)} ml · ${Format.number(s.abv.unwrap)} % · ${Format.number(d.unwrap)} min"
    case Intake.Water(v, _, d) => s"Water · ${Format.number(v.unwrap)} ml · ${Format.number(d.unwrap)} min"
    case Intake.Meal(k, _)     => s"Food · ${Format.number(k.unwrap)} kcal"
  }
}
