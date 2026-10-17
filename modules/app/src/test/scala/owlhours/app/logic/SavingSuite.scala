package owlhours.app.logic

import cats.effect.{Deferred, IO, Ref}
import cats.effect.std.Mutex
import cats.effect.unsafe.implicits.global

class SavingSuite extends munit.FunSuite {
  test("concurrent updates are saved in order, so storage ends with the latest state") {
    (for {
      state <- Ref[IO].of(0)
      saved <- Ref[IO].of(List.empty[Int])
      gate  <- Deferred[IO, Unit]
      lock  <- Mutex[IO]
      // the first save is slow: without the lock, the second save would finish first and be overwritten
      save = (v: Int) => (if v == 1 then gate.get else IO.unit) *> saved.update(_ :+ v)
      first  <- Saving.serialised(state, lock, save)(_ + 1).start
      _      <- IO.cede.replicateA_(10)
      second <- Saving.serialised(state, lock, save)(_ + 1).start
      _      <- IO.cede.replicateA_(10) *> gate.complete(())
      _      <- first.join *> second.join
      result <- saved.get
    } yield assertEquals(result, List(1, 2))).unsafeToFuture()
  }
}
