package owlhours.model

import java.time.Instant
import owlhours.domain.*

final case class BacSample(at: Instant, bacMgDl: Double, bracMgL: Double)
final case class Curve(samples: Vector[BacSample])
final case class Projection(curve: Curve, low: Vector[Double], high: Vector[Double])

object Simulation {

  /** "Back to ~0": 0.1 ‰. */
  val ZeroThresholdMgDl: Double = 0.1 * BacUnit.MgDlPerPermille

  /** Shortest drinking duration the model is fed; a 0-minute shot would otherwise divide by zero. */
  val MinDrinkMinutes: Double = 0.5

  def anthropometrics(p: Profile, at: Instant): Anthropometrics =
    Anthropometrics(
      sex = if p.sex == Sex.Male then 1.0 else 0.0,
      weightKg = p.weight.unwrap,
      heightM = p.height.unwrap / 100.0,
      ageYears = p.ageAt(at).toDouble
    )

  def simulate(
      profile: Profile,
      intakes: Seq[Intake],
      from: Instant,
      to: Instant,
      stepMinutes: Int = 1,
      params: Params = Params.published
  ): Curve = {
    require(to.isAfter(from), s"to ($to) must be after from ($from)")
    require(stepMinutes > 0, "stepMinutes must be positive")
    val leadMin   = intakes.map(_.startsAt).minOption.fold(0.0)(first => math.max(0.0, minutesBetween(first, from) + 1))
    val leadSteps = math.ceil(leadMin / stepMinutes).toInt
    val origin    = from.minusSeconds(leadSteps.toLong * stepMinutes * 60)
    val result =
      Engine.run(
        anthropometrics(profile, from),
        schedule(intakes, origin),
        0.0,
        minutesBetween(origin, to),
        stepMinutes,
        params
      )
    Curve(
      (leadSteps until result.size).map { i =>
        val bac = result.bacMgDl(i)
        // Product rule: below "back to ~0" the breath figure is shown as 0 instead of the model's ~0.02 mg/L intercept.
        val brac = if bac >= ZeroThresholdMgDl then result.bracMgL(i) else 0.0
        BacSample(origin.plusSeconds(math.round(result.times(i) * 60)), bac, brac)
      }.toVector
    )
  }

  /**
   * Central curve plus the person-to-person variation band (pointwise min/max of three runs). The upper edge also
   * includes a classic Widmark estimate (never lower than the model runs).
   */
  def project(profile: Profile, intakes: Seq[Intake], from: Instant, to: Instant, stepMinutes: Int = 1): Projection = {
    val base    = simulate(profile, intakes, from, to, stepMinutes, Params.published)
    val slow    = simulate(profile, intakes, from, to, stepMinutes, Params.slowMetaboliser(Params.published))
    val fast    = simulate(profile, intakes, from, to, stepMinutes, Params.fastMetaboliser(Params.published))
    val runs    = Seq(base, slow, fast).map(_.samples.map(_.bacMgDl))
    val widmark = Widmark.curveMgDl(profile, intakes, base.samples.map(_.at))
    Projection(
      base,
      runs.transpose.map(_.min).toVector,
      runs.transpose.map(_.max).zip(widmark).map((a, b) => math.max(a, b)).toVector
    )
  }

  /**
   * The model's `kcal_liquid_per_vol` is non-alcohol energy: the authors' example gives a 5.1 % drink 129.82 kcal/L,
   * less than its ethanol alone (~282). Feeding total energy would brake gastric emptying ever harder as people drink.
   */
  private[model] def schedule(intakes: Seq[Intake], origin: Instant): Schedule = {
    val drinks = intakes.collect {
      case Intake.Drink(s, start, dur) =>
        DrinkInput(
          minutesBetween(origin, start),
          math.max(dur.unwrap, MinDrinkMinutes),
          s.volume.unwrap / 1000,
          s.abv.unwrap,
          s.nonAlcoholKcalPerLitre
        )
      case Intake.Water(vol, start, dur) =>
        DrinkInput(minutesBetween(origin, start), math.max(dur.unwrap, MinDrinkMinutes), vol.unwrap / 1000, 0, 0)
    }
    val meals = intakes.collect { case Intake.Meal(kcal, at) => MealInput(minutesBetween(origin, at), kcal.unwrap) }
    Schedule(drinks, meals)
  }

  private[model] def minutesBetween(a: Instant, b: Instant): Double = (b.toEpochMilli - a.toEpochMilli) / 60000.0
}
