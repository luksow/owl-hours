package owlhours.app.platform

import cats.effect.IO
import fs2.dom.Window
import org.scalajs.dom
import owlhours.store.KeyValue

/** localStorage can throw (private mode, blocked storage); the app then works without persistence. */
final class BrowserKeyValue(window: Window[IO]) extends KeyValue[IO] {
  def get(key: String): IO[Option[String]]      = IO.defer(window.localStorage.getItem(key)).handleError(_ => None)
  def set(key: String, value: String): IO[Unit] = IO.defer(window.localStorage.setItem(key, value)).handleError(_ => ())
  def remove(key: String): IO[Unit]             = IO.defer(window.localStorage.removeItem(key)).handleError(_ => ())

  def available: IO[Boolean] =
    IO.defer(window.localStorage.setItem("oh.probe", "1") *> window.localStorage.removeItem("oh.probe"))
      .as(true)
      .handleError(_ => false)

  def keys: IO[List[String]] =
    IO(List.tabulate(dom.window.localStorage.length)(i => dom.window.localStorage.key(i))).handleError(_ => Nil)
}
