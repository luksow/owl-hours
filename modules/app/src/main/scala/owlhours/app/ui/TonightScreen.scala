package owlhours.app.ui

import calico.html.io.{*, given}
import cats.effect.{IO, Resource}
import fs2.dom.HtmlElement
import owlhours.app.AppState
import owlhours.catalogue.Catalogue

object TonightScreen:
  def apply(app: AppState, catalogue: Catalogue): Resource[IO, HtmlElement[IO]] = div(h1("Tonight"))
