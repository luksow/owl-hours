package java.time

import Civil.*

final class Duration private (seconds: Long, nanos: Int) extends temporal.TemporalAmount with Comparable[Duration] with java.io.Serializable {
  def getSeconds: Long = seconds
  def getNano: Int     = nanos
  def toMillis: Long   = Math.addExact(Math.multiplyExact(seconds, 1000L), (nanos / 1000000).toLong)
  def toNanos: Long    = Math.addExact(Math.multiplyExact(seconds, 1000000000L), nanos.toLong)
  def toMinutes: Long  = seconds / 60
  def toHours: Long    = seconds / 3600
  def toDays: Long     = seconds / 86400
  def toSeconds: Long  = seconds
  def isNegative: Boolean = seconds < 0
  def isZero: Boolean     = seconds == 0 && nanos == 0

  def plus(d: Duration): Duration  = Duration.ofSeconds(Math.addExact(seconds, d.getSeconds), nanos.toLong + d.getNano)
  def minus(d: Duration): Duration = Duration.ofSeconds(Math.subtractExact(seconds, d.getSeconds), nanos.toLong - d.getNano)
  def plusMinutes(m: Long): Duration = plus(Duration.ofMinutes(m))
  def plusSeconds(s: Long): Duration = plus(Duration.ofSeconds(s))
  def multipliedBy(k: Long): Duration = Duration.ofNanos(Math.multiplyExact(toNanos, k))
  def negated: Duration = Duration.ofSeconds(-seconds, -nanos.toLong)
  def abs: Duration     = if isNegative then negated else this

  def compareTo(o: Duration): Int = {
    val c = java.lang.Long.compare(seconds, o.getSeconds)
    if c != 0 then c else Integer.compare(nanos, o.getNano)
  }

  override def equals(o: Any): Boolean = o match {
    case d: Duration => d.getSeconds == seconds && d.getNano == nanos
    case _           => false
  }
  override def hashCode: Int = (seconds ^ (seconds >>> 32)).toInt + 51 * nanos

  /** As the JDK: PT8H6M12.345S, PT0S. */
  override def toString: String =
    if isZero then "PT0S"
    else {
      val effective = if seconds < 0 && nanos > 0 then seconds + 1 else seconds
      val hours     = effective / 3600
      val minutes   = (effective % 3600) / 60
      val secs      = effective % 60
      val sb        = new StringBuilder("PT")
      if hours != 0 then sb ++= s"${hours}H"
      if minutes != 0 then sb ++= s"${minutes}M"
      if secs != 0 || nanos != 0 then {
        if seconds < 0 && nanos > 0 && secs == 0 then sb ++= "-0" else sb ++= secs.toString
        if nanos > 0 then {
          val n = if seconds < 0 then 1000000000 - nanos else nanos
          sb ++= ("." + pad(n, 9)).reverse.dropWhile(_ == '0').reverse
        }
        sb ++= "S"
      }
      sb.toString
    }
}

object Duration {
  val ZERO: Duration = new Duration(0, 0)

  def ofSeconds(s: Long): Duration = new Duration(s, 0)
  def ofSeconds(s: Long, nanoAdjustment: Long): Duration =
    new Duration(Math.addExact(s, floorDiv(nanoAdjustment, 1000000000L)), floorMod(nanoAdjustment, 1000000000L).toInt)
  def ofMillis(ms: Long): Duration  = new Duration(floorDiv(ms, 1000L), (floorMod(ms, 1000L) * 1000000L).toInt)
  def ofNanos(n: Long): Duration    = ofSeconds(0, n)
  def ofMinutes(m: Long): Duration  = new Duration(Math.multiplyExact(m, 60L), 0)
  def ofHours(h: Long): Duration    = new Duration(Math.multiplyExact(h, 3600L), 0)
  def ofDays(d: Long): Duration     = new Duration(Math.multiplyExact(d, 86400L), 0)

  def between(startInclusive: temporal.Temporal, endExclusive: temporal.Temporal): Duration =
    (startInclusive, endExclusive) match {
      case (start: Instant, end: Instant) =>
        ofSeconds(Math.subtractExact(end.getEpochSecond, start.getEpochSecond), end.getNano.toLong - start.getNano)
      case _ => throw new DateTimeException("Duration.between supports only Instant here")
    }
}
