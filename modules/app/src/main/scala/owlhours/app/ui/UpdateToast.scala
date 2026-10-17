package owlhours.app.ui

import calico.html.io.{*, given}
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.concurrent.SignallingRef
import fs2.dom.HtmlElement
import org.scalajs.dom
import scala.scalajs.js

/** Shown when a new version is ready (main.js dispatches "oh-update-ready"); never reloads on its own. */
object UpdateToast {
  def apply(): Resource[IO, HtmlElement[IO]] =
    SignallingRef[IO].of(false).toResource.flatMap { ready =>
      fs2.dom.events[IO, dom.Event](dom.window, "oh-update-ready").foreach(_ => ready.set(true)).compile.drain.background *>
        div(ready.map {
          case false => div(())
          case true =>
            div(
              cls := "fixed inset-x-3 bottom-24 z-20 flex items-center justify-between rounded-2xl border border-ice/60 bg-surface p-4",
              span("A new version of Owl Hours is ready."),
              button(
                cls := "min-h-12 rounded-xl px-4 font-semibold text-ice",
                onClick --> (_.foreach(_ =>
                  IO(if js.typeOf(js.Dynamic.global.ohApplyUpdate) == "function" then js.Dynamic.global.ohApplyUpdate()).void)),
                "Reload"
              )
            )
        })
    }
}
