package owlhours.app.ui

import calico.html.Modifier
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.concurrent.Signal
import fs2.dom.HtmlElement
import org.scalajs.dom

/** Calico has no SVG tags; generated, escaped markup is mounted through innerHTML. */
final case class InnerHtml(markup: Signal[IO, String])

object InnerHtml:
  // The given lives here so it only applies to InnerHtml arguments (a top-level given makes String children ambiguous).
  given [E <: HtmlElement[IO]]: Modifier[IO, E, InnerHtml] = (m, e) =>
    m.markup.getAndDiscreteUpdates.flatMap { (head, tail) =>
      val set = (s: String) => IO(e.asInstanceOf[dom.HTMLElement].innerHTML = s)
      Resource.eval(set(head)) *> tail.foreach(set).compile.drain.background.void
    }
