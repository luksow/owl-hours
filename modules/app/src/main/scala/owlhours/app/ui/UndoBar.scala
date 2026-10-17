package owlhours.app.ui

import calico.html.io.{*, given}
import cats.effect.{IO, Resource}
import fs2.dom.HtmlElement
import owlhours.app.AppState

object UndoBar {
  def apply(app: AppState): Resource[IO, HtmlElement[IO]] =
    div(app.undo.map(_.map(_.message)).changes.map {
      case None => div(())
      case Some(message) =>
        div(
          cls := "fixed inset-x-3 bottom-24 z-20 flex items-center justify-between rounded-2xl border border-line bg-surface p-4",
          span(message),
          button(cls := "min-h-12 rounded-xl px-4 font-semibold text-ice", onClick --> (_.foreach(_ => app.undoLast)), "Undo")
        )
    })
}
