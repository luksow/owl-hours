package owlhours.app.logic

import cats.effect.{IO, Ref}
import cats.effect.std.Mutex

/** Update-then-save as one step, one at a time, so a slow save can never overwrite a newer one. */
object Saving {
  def serialised[A](state: Ref[IO, A], lock: Mutex[IO], save: A => IO[Unit])(f: A => A): IO[A] =
    lock.lock.surround(state.updateAndGet(f).flatTap(save))
}
