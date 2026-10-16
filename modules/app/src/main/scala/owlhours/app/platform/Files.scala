package owlhours.app.platform

import cats.effect.IO
import org.scalajs.dom
import scala.scalajs.js

/** Local files only: nothing is uploaded anywhere (spec §9). */
object Files:
  def download(name: String, text: String): IO[Unit] = IO {
    val blob = new dom.Blob(js.Array[dom.BlobPart](text), new dom.BlobPropertyBag { `type` = "application/json" })
    val url  = dom.URL.createObjectURL(blob)
    val a    = dom.document.createElement("a").asInstanceOf[dom.HTMLAnchorElement]
    a.href = url
    a.setAttribute("download", name)
    dom.document.body.appendChild(a) // some browsers ignore clicks on detached anchors
    a.click()
    a.remove()
    // Safari (and iOS standalone apps) cancel a download whose URL is revoked at once; FileSaver.js waits 40 s too
    dom.window.setTimeout(() => dom.URL.revokeObjectURL(url), 40000)
  }

  /** The chosen file's text; the input is cleared so choosing the same file again fires again. */
  def read(input: fs2.dom.HtmlInputElement[IO]): IO[Option[String]] =
    val el = input.asInstanceOf[dom.HTMLInputElement]
    IO(Option(el.files).filter(_.length > 0).map(_(0))).flatMap {
      case None       => IO.none
      case Some(file) => IO.fromPromise(IO(file.text())).map(Some(_)) <* IO(el.value = "")
    }
