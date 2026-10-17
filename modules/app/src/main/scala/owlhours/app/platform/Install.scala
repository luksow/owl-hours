package owlhours.app.platform

import cats.effect.IO
import cats.syntax.all.*
import org.scalajs.dom
import owlhours.app.logic.InstallHint
import scala.scalajs.js

/** Reads the install bridge in web/main.js and the platform facts InstallHint needs. */
object Install:
  def env: IO[InstallHint.Env] = IO {
    // js.Dynamic.global is only allowed directly before a member selection, so it is not kept in a val
    val ready =
      js.typeOf(js.Dynamic.global.ohInstallReady) == "function" && js.Dynamic.global.ohInstallReady().asInstanceOf[Boolean]
    val nav           = dom.window.navigator.asInstanceOf[js.Dynamic]
    val touch         = nav.maxTouchPoints.asInstanceOf[js.UndefOr[Int]].getOrElse(0)
    val iosStandalone = nav.standalone.asInstanceOf[js.UndefOr[Boolean]].getOrElse(false)
    val standalone    = dom.window.matchMedia("(display-mode: standalone)").matches || iosStandalone
    InstallHint.Env(ready, InstallHint.isIos(dom.window.navigator.userAgent, touch), standalone)
  }

  def changes: fs2.Stream[IO, Unit] = fs2.dom.events[IO, dom.Event](dom.window, "oh-install-changed").void

  def prompt: IO[Unit] =
    IO(js.typeOf(js.Dynamic.global.ohInstall) == "function").ifM(
      IO.fromPromise(IO(js.Dynamic.global.ohInstall().asInstanceOf[js.Promise[String]])).void,
      IO.unit)
