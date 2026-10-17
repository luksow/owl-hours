package java.time

final class ZoneOffset private (totalSeconds: Int) extends java.io.Serializable {
  def getTotalSeconds: Int = totalSeconds
  override def equals(o: Any): Boolean = o match {
    case z: ZoneOffset => z.getTotalSeconds == totalSeconds
    case _             => false
  }
  override def hashCode: Int = totalSeconds
  override def toString: String = if totalSeconds == 0 then "Z" else s"${totalSeconds}s"
}

object ZoneOffset {
  val UTC: ZoneOffset = new ZoneOffset(0)
}

final class OffsetDateTime private (dateTime: LocalDateTime, offset: ZoneOffset) extends java.io.Serializable {
  def toLocalDateTime: LocalDateTime = dateTime
  def getOffset: ZoneOffset          = offset
  def toLocalDate: LocalDate         = dateTime.toLocalDate
}

object OffsetDateTime {
  def of(dateTime: LocalDateTime, offset: ZoneOffset): OffsetDateTime = new OffsetDateTime(dateTime, offset)
}

final class Period private (years: Int, months: Int, days: Int) extends java.io.Serializable {
  def getYears: Int  = years
  def getMonths: Int = months
  def getDays: Int   = days
}

object Period {
  /** As the JDK: whole years and months from start to end, then days. */
  def between(start: LocalDate, end: LocalDate): Period = {
    var totalMonths = (end.getYear * 12L + end.getMonthValue) - (start.getYear * 12L + start.getMonthValue)
    var days        = end.getDayOfMonth - start.getDayOfMonth
    if totalMonths > 0 && days < 0 then {
      totalMonths -= 1
      days = (end.toEpochDay - start.plusMonths(totalMonths).toEpochDay).toInt
    }
    else if totalMonths < 0 && days > 0 then {
      totalMonths += 1
      days -= end.lengthOfMonth
    }
    new Period((totalMonths / 12).toInt, (totalMonths % 12).toInt, days)
  }
}
