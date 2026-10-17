package owlhours.app.ui

import calico.html.io.{*, given}
import cats.effect.{IO, Resource}
import fs2.dom.HtmlElement

/** The frame shared by the full-page screens (Log, History, Settings, About). */
object Screen {
  val frame = "mx-auto flex min-h-dvh max-w-md flex-col gap-4 p-4"

  def header(title: String, back: IO[Unit], backLabel: String = "Done"): Resource[IO, HtmlElement[IO]] =
    div(
      cls := "flex items-center justify-between",
      h1(cls     := "font-ui text-2xl font-semibold uppercase tracking-wide", title),
      button(cls := "min-h-12 px-3 text-dim", onClick --> (_.foreach(_ => back)), backLabel)
    )

  def section(title: String)(content: Resource[IO, HtmlElement[IO]]*): Resource[IO, HtmlElement[IO]] =
    div(cls := "space-y-3", h2(cls := "font-ui text-lg uppercase tracking-widest text-amber", title), content.toList)

  /** Spec §8.1: the science credit, as on a sign maker's plate. */
  def makersPlate: Resource[IO, HtmlElement[IO]] =
    p(
      cls := "mt-6 rounded-xl border border-line bg-surface-2 px-4 py-3 text-center font-ui text-sm uppercase tracking-widest text-dim",
      "Model inside — Podéus & Cedersund, Linköping University"
    )
}
