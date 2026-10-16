package owlhours.app.ui

import calico.html.io.{*, given}
import cats.effect.{IO, Resource}
import fs2.dom.HtmlElement
import owlhours.app.{AppState, Route}
import owlhours.app.logic.Display
import owlhours.domain.*

object SettingsScreen:
  def apply(app: AppState): Resource[IO, HtmlElement[IO]] =
    app.persisted.get.toResource.flatMap { p0 =>
      div(
        cls := Screen.frame,
        Screen.header("Settings", app.go(Route.Tonight)),
        Screen.section("Profile")(
          p(cls := "text-sm text-dim", "Changing these re-estimates every night, past ones included."),
          ProfileFields(p0.profile, "Save profile", app.updateProfile)),
        Screen.section("Units")(
          choice("Blood alcohol unit", BacUnit.values.toList.map(u => u -> Display.unitName(u)), p0.settings.bacUnit,
            u => app.updateSettings(_.copy(bacUnit = u)))),
        Screen.section("Standard drink")(
          p(cls := "text-sm text-dim", "Used to count standard drinks in History."),
          choice("Standard drink", StandardDrink.values.toList.map(s => s -> Display.standardDrinkName(s)), p0.settings.standardDrink,
            s => app.updateSettings(_.copy(standardDrink = s)))),
        Screen.makersPlate
      )
    }

  private def choice[A](group: String, options: List[(A, String)], current: A, pick: A => IO[Unit]) =
    div(role := List("radiogroup"), aria.label := group, cls := "space-y-2", options.map { (a, text) =>
      label(
        cls := "flex min-h-12 items-center gap-3 rounded-xl border border-line bg-surface px-3",
        input.withSelf { self =>
          (typ := "radio", nameAttr := group, cls := "size-5 accent-pink", checked := (a == current),
            onChange --> (_.foreach(_ => self.checked.get.flatMap(IO.whenA(_)(pick(a))))))
        },
        span(text)
      )
    })
