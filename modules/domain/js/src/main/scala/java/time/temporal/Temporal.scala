package java.time.temporal

// The JDK's signatures name these interfaces, and code compiled for Scala.js resolves java.* against the JDK, so
// they must exist; Owl Hours needs none of their members.
trait TemporalAccessor
trait Temporal extends TemporalAccessor
trait TemporalAmount
