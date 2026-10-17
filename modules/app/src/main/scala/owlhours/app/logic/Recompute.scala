package owlhours.app.logic

import cats.effect.IO
import fs2.Stream
import scala.concurrent.duration.FiniteDuration

object Recompute:
  /**
   * At most one computation per `every`, always with the latest input (fs2's `debounce`). A failed computation yields
   * None, so the preview goes blank instead of the stream (and every later preview) dying.
   */
  def latest[A](triggers: Stream[IO, Unit], every: FiniteDuration)(compute: IO[Option[A]]): Stream[IO, Option[A]] =
    triggers.debounce(every).evalMap(_ => compute.handleError(_ => None))
