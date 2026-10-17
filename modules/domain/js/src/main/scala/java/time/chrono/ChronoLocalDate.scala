package java.time.chrono

/** In the JDK, LocalDate compares through this interface; Owl Hours has only the ISO calendar. */
trait ChronoLocalDate extends java.time.temporal.Temporal with Comparable[ChronoLocalDate] {
  def toEpochDay: Long
}
