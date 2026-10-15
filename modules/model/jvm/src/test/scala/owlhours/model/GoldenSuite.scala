package owlhours.model

import io.circe.Decoder
import io.circe.parser.decode
import scala.io.Source

/**
 * Each scenario's `kcalPerLitre` is the model's `kcal_liquid_per_vol` input, i.e. NON-alcohol energy (the app passes
 * total energy minus ethanol). The older scenarios use larger values and exercise the engine's gastric brake; the
 * `authors-*` scenarios use the authors' own drink specs, the regime the app actually runs in.
 */
class GoldenSuite extends munit.FunSuite:
  final case class P(sex: String, weightKg: Double, heightM: Double, age: Double) derives Decoder
  final case class D(startMin: Double, durationMin: Double, volumeMl: Double, abv: Double, kcalPerLitre: Double)
      derives Decoder
  final case class M(atMin: Double, kcal: Double) derives Decoder
  final case class Scenario(name: String, profile: P, drinks: List[D], meals: List[M], horizonMin: Double, stepMin: Double)
      derives Decoder

  private def resource(name: String): String =
    val src = Source.fromResource(s"golden/$name")
    try src.mkString
    finally src.close()

  private val scenarios = decode[List[Scenario]](resource("scenarios.json")).fold(throw _, identity)

  for s <- scenarios do
    test(s"matches SUND: ${s.name}") {
      val rows = resource(s"${s.name}.csv").linesIterator.drop(1).map(_.split(',').map(_.toDouble)).toVector
      val anth = Anthropometrics(if s.profile.sex == "male" then 1 else 0, s.profile.weightKg, s.profile.heightM, s.profile.age)
      val schedule = Schedule(
        s.drinks.map(d => DrinkInput(d.startMin, d.durationMin, d.volumeMl / 1000, d.abv, d.kcalPerLitre)),
        s.meals.map(m => MealInput(m.atMin, m.kcal))
      )
      val r = Engine.run(anth, schedule, 0, s.horizonMin, s.stepMin)
      assertEquals(r.size, rows.size)
      var worst = (0.0, 0.0, 0.0, 0.0) // (excess, t, ours, theirs)
      for (row, i) <- rows.zipWithIndex do
        val (t, bac, brac) = (row(0), row(1), row(2))
        assertEqualsDouble(r.times(i), t, 1e-9)
        val bacTol  = math.max(0.005 * math.abs(bac), 0.1)
        val bracTol = math.max(0.005 * math.abs(brac), 0.005)
        val excess  = math.abs(r.bacMgDl(i) - bac) / bacTol
        if excess > worst._1 then worst = (excess, t, r.bacMgDl(i), bac)
        assert(math.abs(r.bracMgL(i) - brac) <= bracTol, s"BrAC at t=$t: ours ${r.bracMgL(i)}, SUND $brac")
      assert(worst._1 <= 1.0, s"worst BAC mismatch at t=${worst._2}: ours ${worst._3}, SUND ${worst._4}")
    }
