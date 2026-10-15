package owlhours.app.ui

import calico.html.io.{*, given}
import cats.effect.{IO, Resource}
import fs2.dom.HtmlElement
import owlhours.app.AppState

object LogScreen:
  def apply(app: AppState): Resource[IO, HtmlElement[IO]] = div(h1("Log"))
