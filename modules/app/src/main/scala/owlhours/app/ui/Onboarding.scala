package owlhours.app.ui

import calico.html.io.{*, given}
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.concurrent.{Signal, SignallingRef}
import fs2.dom.HtmlElement
import owlhours.app.AppState
import owlhours.app.logic.BrandSvg

object Onboarding:
  def apply(app: AppState): Resource[IO, HtmlElement[IO]] =
    div(app.persisted.map(_.disclaimerAccepted).changes.map {
      case false => disclaimer(app)
      case true  => profile(app)
    })

  private val card = "mx-auto max-w-md p-6 font-body"

  private def disclaimer(app: AppState): Resource[IO, HtmlElement[IO]] =
    SignallingRef[IO].of(false).toResource.flatMap { understood =>
      div(
        cls := card,
        div(cls := "mx-auto mb-6 w-full max-w-xs", InnerHtml(Signal.constant(BrandSvg.wordmark(lit = true)))),
        h1(cls := "font-ui text-3xl font-semibold uppercase tracking-wide", "Before we start"),
        p(cls := "mt-4", "Owl Hours estimates your blood alcohol from what you log, using a published physiological model."),
        ul(
          cls := "mt-3 list-disc space-y-1 pl-5 text-dim",
          li("It is an estimate, not a measurement. Real values vary from person to person."),
          li("Never use it to decide whether you can drive. No number on this app means it is safe to drive."),
          li("Only for adults of legal drinking age where you are.")
        ),
        label(
          cls := "mt-5 flex min-h-12 items-center gap-3",
          input.withSelf { self =>
            (typ := "checkbox", cls := "size-6 accent-pink",
              onChange --> (_.foreach(_ => self.checked.get.flatMap(understood.set))))
          },
          span("I understand")
        ),
        button(
          cls := "mt-4 min-h-14 w-full rounded-2xl border border-pink bg-surface-2 text-xl font-bold uppercase tracking-wider text-pink disabled:opacity-40",
          disabled <-- understood.map(!_),
          onClick --> (_.foreach(_ => app.acceptDisclaimer)),
          "Continue"
        )
      )
    }

  private def profile(app: AppState): Resource[IO, HtmlElement[IO]] =
    div(
      cls := card,
      h1(cls := "font-ui text-3xl font-semibold uppercase tracking-wide", "About you"),
      p(cls := "mt-2 text-dim", "The model uses these to size your blood, body water and liver. They stay on this device."),
      ProfileFields(None, "Save", app.saveProfile)
    )
