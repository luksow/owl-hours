package owlhours.app

import calico.*
import calico.html.io.{*, given}
import cats.effect.*
import fs2.dom.HtmlElement

object Main extends IOWebApp:
  def render: Resource[IO, HtmlElement[IO]] =
    div(cls := "min-h-dvh bg-wall text-ink font-ui p-4", h1(cls := "font-display text-5xl text-pink", "Owl Hours"))
