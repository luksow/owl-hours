package owlhours.app.ui

import calico.html.io.{*, given}
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.concurrent.SignallingRef
import fs2.dom.HtmlElement
import owlhours.app.AppState
import owlhours.app.logic.ProfileForm

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
    (
      SignallingRef[IO].of(ProfileForm.Fields("", "", "", "")),
      SignallingRef[IO].of(List.empty[String])
    ).tupled.toResource.flatMap { (fields, errors) =>
      def text(labelText: String, update: (ProfileForm.Fields, String) => ProfileForm.Fields, mode: String) =
        label(
          cls := "mt-4 block",
          span(cls := "font-ui text-sm uppercase tracking-wider text-dim", labelText),
          input.withSelf { self =>
            (cls := "mt-1 min-h-12 w-full rounded-xl border border-line bg-surface px-3 text-lg text-ink",
              typ := "text", inputMode := mode,
              onInput --> (_.foreach(_ => self.value.get.flatMap(v => fields.update(update(_, v))))))
          }
        )
      def sexOption(value: String, text: String) =
        label(
          cls := "flex min-h-12 flex-1 items-center gap-2 rounded-xl border border-line bg-surface px-3",
          input.withSelf { self =>
            (typ := "radio", nameAttr := "sex", cls := "size-5 accent-pink",
              onChange --> (_.foreach(_ => fields.update(_.copy(sex = value)))))
          },
          span(text)
        )
      div(
        cls := card,
        h1(cls := "font-ui text-3xl font-semibold uppercase tracking-wide", "About you"),
        p(cls := "mt-2 text-dim", "The model uses these to size your blood, body water and liver. They stay on this device."),
        div(cls := "mt-4 flex gap-3", sexOption("female", "Female"), sexOption("male", "Male")),
        p(cls := "mt-1 text-xs text-dim", "Sex is used only by the blood-volume and body-water formulas."),
        text("Age", (f, v) => f.copy(age = v), "numeric"),
        text("Weight (kg)", (f, v) => f.copy(weightKg = v), "decimal"),
        text("Height (cm)", (f, v) => f.copy(heightCm = v), "decimal"),
        ul(cls := "mt-4 space-y-1 text-sm text-warn", children <-- errors.map(_.map(e => li(e)))),
        button(
          cls := "mt-4 min-h-14 w-full rounded-2xl border border-pink bg-surface-2 text-xl font-bold uppercase tracking-wider text-pink",
          onClick --> (_.foreach(_ =>
            fields.get.map(ProfileForm.validate).flatMap {
              case Right(p)   => errors.set(Nil) *> app.saveProfile(p)
              case Left(errs) => errors.set(errs.toList)
            })),
          "Save"
        )
      )
    }
