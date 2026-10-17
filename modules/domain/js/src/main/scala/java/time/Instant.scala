package java.time

import Civil.*

final class Instant private (seconds: Long, nanos: Int)
    extends temporal.Temporal
    with Comparable[Instant]
    with java.io.Serializable {
  def getEpochSecond: Long = seconds
  def getNano: Int         = nanos
  def toEpochMilli: Long   = Math.addExact(Math.multiplyExact(seconds, 1000L), (nanos / 1000000).toLong)

  def plusSeconds(s: Long): Instant  = Instant.ofEpochSecond(Math.addExact(seconds, s), nanos.toLong)
  def minusSeconds(s: Long): Instant = Instant.ofEpochSecond(Math.subtractExact(seconds, s), nanos.toLong)
  def plusMillis(ms: Long): Instant  = plus(Duration.ofMillis(ms))
  def minusMillis(ms: Long): Instant = minus(Duration.ofMillis(ms))
  def plusNanos(n: Long): Instant    = Instant.ofEpochSecond(seconds, nanos + n)
  def plus(amount: temporal.TemporalAmount): Instant = {
    val d = Instant.duration(amount)
    Instant.ofEpochSecond(Math.addExact(seconds, d.getSeconds), nanos.toLong + d.getNano)
  }
  def minus(amount: temporal.TemporalAmount): Instant = {
    val d = Instant.duration(amount)
    Instant.ofEpochSecond(Math.subtractExact(seconds, d.getSeconds), nanos.toLong - d.getNano)
  }

  def isAfter(o: Instant): Boolean  = compareTo(o) > 0
  def isBefore(o: Instant): Boolean = compareTo(o) < 0
  def compareTo(o: Instant): Int = {
    val c = java.lang.Long.compare(seconds, o.getEpochSecond)
    if c != 0 then c else Integer.compare(nanos, o.getNano)
  }

  def atOffset(offset: ZoneOffset): OffsetDateTime =
    OffsetDateTime.of(LocalDateTime.ofEpochSecond(seconds, nanos, offset), offset)

  override def equals(o: Any): Boolean = o match {
    case i: Instant => i.getEpochSecond == seconds && i.getNano == nanos
    case _          => false
  }
  override def hashCode: Int = (seconds ^ (seconds >>> 32)).toInt + 51 * nanos

  /** As the JDK's ISO_INSTANT: seconds always, the fraction in groups of three digits when not zero. */
  override def toString: String = {
    val dt = LocalDateTime.ofEpochSecond(seconds, nanos, ZoneOffset.UTC)
    val fraction =
      if nanos == 0 then ""
      else if nanos % 1000000 == 0 then "." + pad(nanos / 1000000, 3)
      else if nanos % 1000 == 0 then "." + pad(nanos / 1000, 6)
      else "." + pad(nanos, 9)
    s"${dt.toLocalDate}T${pad(dt.getHour, 2)}:${pad(dt.getMinute, 2)}:${pad(dt.getSecond, 2)}${fraction}Z"
  }
}

object Instant {
  val EPOCH: Instant = new Instant(0, 0)

  private def duration(amount: temporal.TemporalAmount): Duration = amount match {
    case d: Duration => d
    case _           => throw new DateTimeException("Only a Duration can be added to an Instant here")
  }

  def ofEpochSecond(s: Long): Instant = new Instant(s, 0)
  def ofEpochSecond(s: Long, nanoAdjustment: Long): Instant =
    new Instant(Math.addExact(s, floorDiv(nanoAdjustment, 1000000000L)), floorMod(nanoAdjustment, 1000000000L).toInt)
  def ofEpochMilli(ms: Long): Instant = new Instant(floorDiv(ms, 1000L), (floorMod(ms, 1000L) * 1000000L).toInt)

  /** `yyyy-MM-ddTHH:mm:ss[.f{1,9}]Z`, as ISO_INSTANT reads what Owl Hours and browsers write. */
  def parse(text: CharSequence): Instant = {
    val s    = text.toString
    def fail = throw new format.DateTimeParseException(s"Text '$s' could not be parsed", s, 0)
    if s.length < 17 || !(s.endsWith("Z") || s.endsWith("z")) || (s.charAt(10) != 'T' && s.charAt(10) != 't') then fail
    val date =
      try LocalDate.parse(s.substring(0, 10))
      catch case _: DateTimeException => fail
    if !digits(s, 11, 2) || s.charAt(13) != ':' || !digits(s, 14, 2) then fail
    val (hh, mm) = (s.substring(11, 13).toInt, s.substring(14, 16).toInt)
    val rest     = s.substring(16, s.length - 1)
    val (ss, nanos) =
      if rest.length >= 3 && rest.charAt(0) == ':' && digits(rest, 1, 2) then {
        val frac = rest.substring(3)
        if frac.isEmpty then (rest.substring(1, 3).toInt, 0)
        else if frac.length >= 2 && frac.length <= 10 && frac.charAt(0) == '.' && digits(frac, 1, frac.length - 1) then
          (rest.substring(1, 3).toInt, (frac.substring(1) + "00000000").take(9).toInt)
        else fail
      } else fail
    // like ISO_INSTANT, 24:00:00 is the end of the day
    val endOfDay = hh == 24 && mm == 0 && ss == 0 && nanos == 0
    if (hh > 23 && !endOfDay) || mm > 59 || ss > 59 then fail
    ofEpochSecond(date.toEpochDay * SecondsPerDay + hh * 3600L + mm * 60L + ss, nanos.toLong)
  }
}
