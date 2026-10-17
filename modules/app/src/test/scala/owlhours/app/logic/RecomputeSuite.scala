package owlhours.app.logic

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import fs2.Stream
import scala.concurrent.duration.DurationInt

class RecomputeSuite extends munit.FunSuite {
  test("a failing computation clears the result, and the next trigger still computes") {
    (for {
      calls <- Ref[IO].of(0)
      compute = calls.updateAndGet(_ + 1).flatMap(n => if n == 1 then IO.raiseError(new RuntimeException("boom")) else IO.pure(Some(n)))
      out <- Recompute.latest(Stream(()) ++ Stream.sleep_[IO](100.millis) ++ Stream(()), 10.millis)(compute).compile.toList
    }
    yield assertEquals(out, List(None, Some(2)))).unsafeToFuture()
  }

  test("a burst of triggers recomputes at most once per period, with the latest input") {
    (for {
      calls <- Ref[IO].of(0)
      out   <- Recompute.latest(Stream.emits(List.fill(20)(())), 50.millis)(calls.updateAndGet(_ + 1).map(Some(_))).compile.toList
    }
    yield assertEquals(out, List(Some(1)))).unsafeToFuture()
  }
}
