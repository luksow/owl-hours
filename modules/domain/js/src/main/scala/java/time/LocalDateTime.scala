package java.time

import Civil.*

/** Only what UTC arithmetic needs: built from epoch seconds and read back field by field. */
final class LocalDateTime private (date: LocalDate, secondOfDay: Int, nanos: Int) extends java.io.Serializable {
  def toLocalDate: LocalDate = date
  def getYear: Int           = date.getYear
  def getMonthValue: Int     = date.getMonthValue
  def getDayOfMonth: Int     = date.getDayOfMonth
  def getHour: Int           = secondOfDay / 3600
  def getMinute: Int         = (secondOfDay / 60) % 60
  def getSecond: Int         = secondOfDay        % 60
  def getNano: Int           = nanos

  override def equals(o: Any): Boolean = o match {
    case d: LocalDateTime =>
      d.toLocalDate == date && d.getHour * 3600 + d.getMinute * 60 + d.getSecond == secondOfDay && d.getNano == nanos
    case _ => false
  }
  override def hashCode: Int = date.hashCode ^ (secondOfDay * 31 + nanos)
}

object LocalDateTime {
  def ofEpochSecond(epochSecond: Long, nanos: Int, offset: ZoneOffset): LocalDateTime = {
    val local = Math.addExact(epochSecond, offset.getTotalSeconds.toLong)
    new LocalDateTime(LocalDate.ofEpochDay(floorDiv(local, SecondsPerDay)), floorMod(local, SecondsPerDay).toInt, nanos)
  }
}
