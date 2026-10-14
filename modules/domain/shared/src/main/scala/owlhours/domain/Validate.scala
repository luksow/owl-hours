package owlhours.domain

private[domain] object Validate:
  /** Formats a number the same way on JVM and Scala.js ("5", not "5.0"). */
  def fmt(x: Double): String =
    if x == math.rint(x) && math.abs(x) < 1e15 then x.toLong.toString else x.toString

  def range(label: String, lo: Double, hi: Double, unit: String, loExclusive: Boolean = false)(
      x: Double
  ): Either[String, Double] =
    if x.isNaN || x.isInfinite then Left(s"$label must be a number")
    else if (if loExclusive then x <= lo else x < lo) || x > hi then
      val lower = if loExclusive then s"above ${fmt(lo)} and at most" else s"between ${fmt(lo)} and"
      Left(s"$label must be $lower ${fmt(hi)} $unit, got ${fmt(x)}")
    else Right(x)

  def intRange(label: String, lo: Int, hi: Int, unit: String)(x: Int): Either[String, Int] =
    if x < lo || x > hi then Left(s"$label must be between $lo and $hi $unit, got $x") else Right(x)
