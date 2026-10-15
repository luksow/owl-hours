package owlhours.app.platform

import cats.effect.IO
import java.time.Instant
import scala.scalajs.js

/**
 * Wall-clock time from `Date.now()`. Not `IO.realTime`: on Scala.js that is `performance.timeOrigin + performance.now()`,
 * a monotonic clock that can stall while a phone sleeps, and nights are about wall-clock time.
 */
object Now:
  val instant: IO[Instant] = IO(Instant.ofEpochMilli(js.Date.now().toLong))
