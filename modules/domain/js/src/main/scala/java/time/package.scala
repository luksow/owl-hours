package java.time

/**
 * The slice of java.time Owl Hours uses, for Scala.js only (the JVM uses the JDK's own classes). scala-java-time is
 * a complete port, with formatters, chronologies and locale data, and was ~9 % of the bundle (learning note 06).
 * Semantics follow the JDK: the shared test suites run against the JDK on the JVM and against these on Node.
 *
 * Code compiled for Scala.js resolves java.* names against the JDK, and an incremental compile of one file here can
 * too. So these classes keep the JDK's signatures, and refer to each other only through members the JDK also has.
 */
private[time] object Civil:
  val SecondsPerDay = 86400L

  def floorDiv(a: Long, b: Long): Long = Math.floorDiv(a, b)
  def floorMod(a: Long, b: Long): Long = Math.floorMod(a, b)

  def isLeap(y: Long): Boolean = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

  def monthLength(y: Long, m: Int): Int = m match
    case 2              => if isLeap(y) then 29 else 28
    case 4 | 6 | 9 | 11 => 30
    case _              => 31

  /** Days since 1970-01-01 of a proleptic Gregorian date (H. Hinnant's days_from_civil). */
  def daysFromCivil(y0: Long, m: Int, d: Int): Long =
    val y   = if m <= 2 then y0 - 1 else y0
    val era = floorDiv(y, 400)
    val yoe = y - era * 400
    val doy = (153 * (if m > 2 then m - 3 else m + 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    era * 146097 + doe - 719468

  def civilFromDays(z0: Long): (Long, Int, Int) =
    val z   = z0 + 719468
    val era = floorDiv(z, 146097)
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp  = (5 * doy + 2) / 153
    val d   = (doy - (153 * mp + 2) / 5 + 1).toInt
    val m   = (if mp < 10 then mp + 3 else mp - 9).toInt
    (yoe + era * 400 + (if m <= 2 then 1 else 0), m, d)

  def pad(n: Long, width: Int): String =
    val s = math.abs(n).toString
    (if n < 0 then "-" else "") + "0" * math.max(0, width - s.length) + s

  def digits(s: CharSequence, from: Int, n: Int): Boolean =
    from + n <= s.length && (from until from + n).forall(i => s.charAt(i) >= '0' && s.charAt(i) <= '9')
