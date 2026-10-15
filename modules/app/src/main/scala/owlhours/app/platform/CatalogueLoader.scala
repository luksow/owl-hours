package owlhours.app.platform

import cats.effect.IO
import org.scalajs.dom
import owlhours.catalogue.Catalogue

object CatalogueLoader:
  def load: IO[Either[List[String], Catalogue]] =
    IO.fromPromise(IO(dom.fetch("catalogue.json")))
      .flatMap { response =>
        if response.ok then IO.fromPromise(IO(response.text())).map(Catalogue.fromJson(_).left.map(_.toList))
        else IO.pure(Left(List(s"catalogue.json: HTTP ${response.status}")))
      }
      .handleError(e => Left(List(s"Could not load the drink catalogue: ${e.getMessage}")))
