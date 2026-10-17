package owlhours.model

import java.time.Instant
import owlhours.domain.*

/**
 * Classic Widmark estimate used only as the upper edge of the variation band: ethanol absorbed linearly over the
 * drinking time, distributed in r × body weight, eliminated at 0.10 ‰/h (slow end of the healthy range, Jones 2010).
 * It is deliberately conservative where the physiological model predicts slow gastric emptying.
 */
object Widmark {
  val EliminationPermillePerHour: Double = 0.10

  def r(sex: Sex): Double = sex match {
    case Sex.Male   => 0.68
    case Sex.Female => 0.55
  }

  def curveMgDl(profile: Profile, intakes: Seq[Intake], times: Vector[Instant]): Vector[Double] = {
    val drinks = intakes.collect { case d: Intake.Drink => d }
    if drinks.isEmpty || times.isEmpty then Vector.fill(times.size)(0.0)
    else {
      val distribution = r(profile.sex) * profile.weight.unwrap // kg
      def absorbedGrams(t: Long): Double = drinks.map { d =>
        val start    = d.start.toEpochMilli
        val duration = math.max(d.duration.unwrap, Simulation.MinDrinkMinutes) * 60000
        d.snapshot.ethanolGrams * math.min(1.0, math.max(0.0, (t - start) / duration))
      }.sum
      // integrate from the first drink so earlier drinks count, then sample at `times`
      val firstStart = drinks.map(_.start.toEpochMilli).min
      val stepMs     = 60000L
      var t          = math.min(firstStart, times.head.toEpochMilli)
      var permille   = 0.0
      var absorbed   = absorbedGrams(t)
      val out        = Vector.newBuilder[Double]
      var i          = 0
      while i < times.size do {
        val target = times(i).toEpochMilli
        while t < target do {
          val next   = math.min(t + stepMs, target)
          val now    = absorbedGrams(next)
          permille   = math.max(0.0, permille + (now - absorbed) / distribution - EliminationPermillePerHour * (next - t) / 3600000.0)
          absorbed   = now
          t          = next
        }
        out += permille * BacUnit.MgDlPerPermille
        i += 1
      }
      out.result()
    }
  }
}
