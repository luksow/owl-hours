package java.time

import Civil.*

final class LocalDate private (year: Int, month: Int, day: Int) extends chrono.ChronoLocalDate with java.io.Serializable:
  def getYear: Int       = year
  def getMonthValue: Int = month
  def getDayOfMonth: Int = day
  def isLeapYear: Boolean = isLeap(year)
  def lengthOfMonth: Int  = monthLength(year, month)
  def toEpochDay: Long    = daysFromCivil(year, month, day)

  def plusDays(n: Long): LocalDate  = LocalDate.ofEpochDay(Math.addExact(toEpochDay, n))
  def minusDays(n: Long): LocalDate = LocalDate.ofEpochDay(Math.subtractExact(toEpochDay, n))
  def plusMonths(n: Long): LocalDate =
    val total = year * 12L + (month - 1) + n
    val y     = floorDiv(total, 12)
    val m     = floorMod(total, 12).toInt + 1
    LocalDate.of(y.toInt, m, math.min(day, monthLength(y, m)))
  def minusMonths(n: Long): LocalDate = plusMonths(-n)
  def plusYears(n: Long): LocalDate   = plusMonths(Math.multiplyExact(n, 12L))
  def minusYears(n: Long): LocalDate  = plusYears(-n)

  def isAfter(o: chrono.ChronoLocalDate): Boolean  = compareTo(o) > 0
  def isBefore(o: chrono.ChronoLocalDate): Boolean = compareTo(o) < 0
  def isEqual(o: chrono.ChronoLocalDate): Boolean  = compareTo(o) == 0
  def compareTo(o: chrono.ChronoLocalDate): Int    = java.lang.Long.compare(toEpochDay, o.toEpochDay)

  override def equals(o: Any): Boolean = o match
    case d: LocalDate => d.getYear == year && d.getMonthValue == month && d.getDayOfMonth == day
    case _            => false
  override def hashCode: Int = (year & 0xfffff800) ^ ((year << 11) + (month << 6) + day)

  /** As the JDK: yyyy-MM-dd, with a sign and more digits outside 0000–9999. */
  override def toString: String =
    val y =
      if math.abs(year) < 1000 then (if year < 0 then "-" + pad(-year, 4) else pad(year, 4))
      else if year > 9999 then "+" + year
      else year.toString
    s"$y-${pad(month, 2)}-${pad(day, 2)}"

object LocalDate:
  def of(year: Int, month: Int, day: Int): LocalDate =
    if year < -999999999 || year > 999999999 then throw new DateTimeException(s"Invalid value for Year: $year")
    if month < 1 || month > 12 then throw new DateTimeException(s"Invalid value for MonthOfYear: $month")
    if day < 1 || day > 31 then throw new DateTimeException(s"Invalid value for DayOfMonth: $day")
    if day > monthLength(year, month) then throw new DateTimeException(s"Invalid date '$year-$month-$day'")
    new LocalDate(year, month, day)

  def ofEpochDay(epochDay: Long): LocalDate =
    val (y, m, d) = civilFromDays(epochDay)
    new LocalDate(y.toInt, m, d)

  /** `yyyy-MM-dd`. */
  def parse(text: CharSequence): LocalDate =
    val s = text.toString
    if s.length != 10 || !digits(s, 0, 4) || s.charAt(4) != '-' || !digits(s, 5, 2) || s.charAt(7) != '-' || !digits(s, 8, 2) then
      throw new format.DateTimeParseException(s"Text '$s' could not be parsed", s, 0)
    try of(s.substring(0, 4).toInt, s.substring(5, 7).toInt, s.substring(8, 10).toInt)
    catch case e: DateTimeException => throw new format.DateTimeParseException(s"Text '$s' could not be parsed: ${e.getMessage}", s, 0)
