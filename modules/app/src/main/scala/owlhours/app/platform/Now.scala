package owlhours.app.platform

import cats.effect.IO
import java.time.Instant

/** IO.realTimeInstant exists only on the JVM; this is the Scala.js-safe equivalent. */
object Now:
  val instant: IO[Instant] = IO.realTime.map(d => Instant.ofEpochMilli(d.toMillis))
