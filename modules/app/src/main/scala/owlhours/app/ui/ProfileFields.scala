package owlhours.app.ui

import calico.html.io.{*, given}
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.concurrent.SignallingRef
import fs2.dom.HtmlElement
import java.time.ZoneOffset
import owlhours.app.logic.ProfileForm
import owlhours.app.platform.Now
import owlhours.domain.*

/** The body-data form, empty for onboarding or filled from a saved profile in Settings. */
object ProfileFields {
  def apply(initial: Option[Profile], submit: String, save: Profile => IO[Unit]): Resource[IO, HtmlElement[IO]] = {
    val start = initial.fold(ProfileForm.Fields("", "", "", ""))(ProfileForm.Fields.of)
    (
      SignallingRef[IO].of(start),
      SignallingRef[IO].of(List.empty[String]),
      SignallingRef[IO].of(false),
      Now.instant.map(_.atOffset(ZoneOffset.UTC).toLocalDate)
    ).tupled.toResource.flatMap { (fields, errors, saved, today) =>
      def text(labelText: String, value0: String, update: (ProfileForm.Fields, String) => ProfileForm.Fields, mode: String,
          tpe: String = "text", max: Option[String] = None) =
        label(
          cls := "mt-4 block",
          span(cls := "font-ui text-sm uppercase tracking-wider text-dim", labelText),
          input.withSelf { self =>
            (cls := "mt-1 min-h-12 w-full rounded-xl border border-line bg-surface px-3 text-lg text-ink",
              typ := tpe, value := value0, Option.when(tpe != "date")(inputMode := mode), max.map(m => maxAttr := m),
              onInput --> (_.foreach(_ => self.value.get.flatMap(v => saved.set(false) *> fields.update(update(_, v))))))
          }
        )
      def sexOption(value0: String, text: String) =
        label(
          cls := "flex min-h-12 flex-1 items-center gap-2 rounded-xl border border-line bg-surface px-3",
          input.withSelf { self =>
            (typ := "radio", nameAttr := "sex", cls := "size-5 accent-pink", checked := (start.sex == value0),
              onChange --> (_.foreach(_ => saved.set(false) *> fields.update(_.copy(sex = value0)))))
          },
          span(text)
        )
      div(
        div(cls := "mt-4 flex gap-3", sexOption("female", "Female"), sexOption("male", "Male")),
        p(cls := "mt-1 text-xs text-dim", "Sex is used only by the blood-volume and body-water formulas."),
        text("Date of birth", start.born, (f, v) => f.copy(born = v), "numeric", "date", Some(IsoTime.formatDate(today.minusYears(18)))),
        text("Weight (kg)", start.weightKg, (f, v) => f.copy(weightKg = v), "decimal"),
        text("Height (cm)", start.heightCm, (f, v) => f.copy(heightCm = v), "decimal"),
        ul(role := List("alert"), cls := "mt-4 space-y-1 text-sm text-warn", children <-- errors.map(_.map(e => li(e)))),
        p(cls := "mt-2 text-sm text-ice", saved.map(if _ then "Saved." else "")),
        button(
          cls := "mt-4 min-h-14 w-full rounded-2xl border border-pink bg-surface-2 text-xl font-bold uppercase tracking-wider text-pink",
          onClick --> (_.foreach(_ =>
            fields.get.map(ProfileForm.validate(_, today)).flatMap {
              case Right(p)   => errors.set(Nil) *> save(p) *> saved.set(true)
              case Left(errs) => errors.set(errs.toList)
            })),
          submit
        )
      )
    }
  }
}
