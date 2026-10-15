package owlhours.model

/** One drink as a constant-rate input in model units. */
final case class DrinkInput(startMin: Double, durationMin: Double, volumeL: Double, abvPercent: Double, kcalPerLitre: Double):
  def endMin: Double       = startMin + durationMin
  def rateLPerMin: Double  = volumeL / durationMin

final case class MealInput(atMin: Double, kcal: Double)

/** `drinkStarts`: a drink begins at `start`, which re-bases the gastric calorie brake (new_drink). */
final case class Segment(start: Double, end: Double, inputs: Inputs, mealKcal: Double, drinkStarts: Boolean)

final case class Schedule(drinks: Seq[DrinkInput], meals: Seq[MealInput]):
  /** Drinks active at `t` combined into one input, weighted by volume rate (exact for the model's linear input terms). */
  def inputsAt(t: Double): Inputs =
    val active = drinks.filter(d => d.startMin <= t && t < d.endMin)
    val total  = active.map(_.rateLPerMin).sum
    if total <= 0 then Inputs.zero
    else
      Inputs(
        etohConcPercent = active.map(d => d.rateLPerMin * d.abvPercent).sum / total,
        volRateLPerMin = total,
        kcalPerLitre = active.map(d => d.rateLPerMin * d.kcalPerLitre).sum / total
      )

  /** Splits [from, to] at every drink start/end and meal time. Meals exactly at `to` are ignored. */
  def segments(from: Double, to: Double): Vector[Segment] =
    val cuts = (Seq(from, to) ++ drinks.flatMap(d => Seq(d.startMin, d.endMin)) ++ meals.map(_.atMin))
      .filter(t => t >= from && t <= to)
      .distinct
      .sorted
    cuts
      .zip(cuts.tail)
      .map((a, b) => Segment(a, b, inputsAt(a), meals.filter(_.atMin == a).map(_.kcal).sum, drinks.exists(_.startMin == a)))
      .toVector
