package owlhours.domain

object Format {

  /** Formats a number the same way on JVM and Scala.js ("5", not "5.0"). */
  def number(x: Double): String =
    if x == math.rint(x) && math.abs(x) < 1e15 then x.toLong.toString else x.toString
}
