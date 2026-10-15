package owlhours.app.ui

import calico.html.io.{*, given}
import cats.effect.{IO, Resource}
import fs2.concurrent.SignallingRef
import fs2.dom.HtmlElement
import owlhours.app.{AppState, Route}

/** Common frame for the logging sheets: title, Cancel, content, then the actions. */
object Sheet:
  def apply(title: String, app: AppState)(content: Resource[IO, HtmlElement[IO]]*): Resource[IO, HtmlElement[IO]] =
    div(
      cls := "mx-auto flex min-h-dvh max-w-md flex-col gap-4 p-4",
      div(cls := "flex items-center justify-between",
        h1(cls := "font-ui text-2xl font-semibold uppercase tracking-wide", title),
        button(cls := "min-h-12 px-3 text-dim", onClick --> (_.foreach(_ => app.go(Route.Tonight))), "Cancel")),
      content.toList
    )

  def errors(es: SignallingRef[IO, List[String]]): Resource[IO, HtmlElement[IO]] =
    ul(role := List("alert"), cls := "space-y-1 text-warn", children <-- es.map(_.map(e => li(e))))

  def addButton(action: IO[Unit]): Resource[IO, HtmlElement[IO]] =
    button(
      cls := "min-h-14 w-full rounded-2xl border border-pink bg-surface-2 text-xl font-bold uppercase text-pink",
      onClick --> (_.foreach(_ => action)),
      "Add"
    )
