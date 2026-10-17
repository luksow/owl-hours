package owlhours.app.platform

import cats.effect.unsafe.IORuntime
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.concurrent.{Signal, SignallingRef}
import org.scalajs.dom
import owlhours.app.logic.InstallHint
import scala.scalajs.js

/** Reads the install bridge in web/main.js and the platform facts InstallHint needs. */
object Install {
  def env: IO[InstallHint.Env] = IO {
    // js.Dynamic.global is only allowed directly before a member selection, so it is not kept in a val
    val ready =
      js.typeOf(js.Dynamic.global.ohInstallReady) == "function" && js.Dynamic.global
        .ohInstallReady()
        .asInstanceOf[Boolean]
    val nav           = dom.window.navigator.asInstanceOf[js.Dynamic]
    val touch         = nav.maxTouchPoints.asInstanceOf[js.UndefOr[Int]].getOrElse(0)
    val iosStandalone = nav.standalone.asInstanceOf[js.UndefOr[Boolean]].getOrElse(false)
    val standalone    = dom.window.matchMedia("(display-mode: standalone)").matches || iosStandalone
    InstallHint.Env(ready, InstallHint.isIos(dom.window.navigator.userAgent, touch), standalone)
  }

  /**
   * `env`, kept current by web/main.js's "oh-install-changed". The listener is attached before `env` is read again,
   * so an offer that lands while the app starts up is seen either by that read or by the listener.
   */
  def signal: Resource[IO, Signal[IO, InstallHint.Env]] =
    for {
      ref <- env.flatMap(SignallingRef[IO].of).toResource
      refresh = env.flatMap(ref.set)
      _ <- Resource.make(IO {
        // a plain DOM listener starts the refresh on the app's runtime (a Dispatcher would add ~14 KB to the bundle)
        val listener: js.Function1[dom.Event, Unit] = _ => refresh.unsafeRunAndForget()(using IORuntime.global)
        dom.window.addEventListener("oh-install-changed", listener)
        listener
      })(listener => IO(dom.window.removeEventListener("oh-install-changed", listener)))
      _ <- refresh.toResource
    } yield ref

  /** A failed prompt is not an error the app can act on; the bridge reports the change either way. */
  def prompt: IO[Unit] =
    IO(js.typeOf(js.Dynamic.global.ohInstall) == "function").ifM(
      IO.fromPromise(IO(js.Dynamic.global.ohInstall().asInstanceOf[js.Promise[String]])).void.attempt.void,
      IO.unit
    )
}
