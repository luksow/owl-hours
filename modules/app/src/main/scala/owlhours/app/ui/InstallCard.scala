package owlhours.app.ui

import calico.html.io.{*, given}
import cats.Eq
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.dom.HtmlElement
import owlhours.app.AppState
import owlhours.app.logic.InstallHint

/** Spec §10: an install button where the browser offers one, the Share → Add to Home Screen hint on iOS. */
object InstallCard {
  private val Title  = "Keep Owl Hours on your home screen"
  private val Button = "min-h-12 rounded-xl px-4 font-semibold"

  def tonight(app: AppState): Resource[IO, HtmlElement[IO]] =
    div((app.install, app.installHintDismissed).mapN((h, d) => h.filterNot(_ => d)).changes(using Eq.fromUniversalEquals).map {
      case None    => div(())
      case Some(h) => card(app, h, dismissible = true, margin = "mx-5 mt-4")
    })

  def settings(app: AppState): Resource[IO, HtmlElement[IO]] =
    // `contents` and `hidden` keep an absent card from taking a gap in the Settings column
    div(cls := "contents", app.install.changes(using Eq.fromUniversalEquals).map {
      case None    => div(cls := "hidden")
      case Some(h) => Screen.section("Install")(card(app, h, dismissible = false, margin = ""))
    })

  private def card(app: AppState, h: InstallHint, dismissible: Boolean, margin: String): Resource[IO, HtmlElement[IO]] = {
    val (text, actions) = h match {
      case InstallHint.Prompt =>
        ("It opens like an app and works offline, even in a basement bar.",
          List(button(cls := s"$Button bg-surface-2 text-ice", onClick --> (_.foreach(_ => app.promptInstall)), "Install")) ++
            Option.when(dismissible)(button(cls := s"$Button text-dim", onClick --> (_.foreach(_ => app.dismissInstallHint)), "Not now")))
      case InstallHint.IosShare =>
        ("Tap Share, then Add to Home Screen. It works offline after that.",
          Option.when(dismissible)(button(cls := s"$Button text-ice", onClick --> (_.foreach(_ => app.dismissInstallHint)), "Got it")).toList)
    }
    sectionTag(
      aria.label := "Install Owl Hours",
      cls := s"$margin rounded-2xl border border-ice/60 bg-surface p-4 font-body",
      p(cls := "font-ui text-lg font-semibold", Title),
      p(cls := "mt-1 text-sm text-dim", text),
      div(cls := "mt-3 flex gap-2", actions)
    )
  }
}
