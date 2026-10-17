package owlhours.app.ui

import calico.html.io.{*, given}
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.concurrent.SignallingRef
import fs2.dom.HtmlElement
import owlhours.app.{AppState, Route}
import owlhours.app.logic.Display
import owlhours.app.platform.{Files, LocalDateTime, Now}
import owlhours.domain.*
import owlhours.store.{Backup, Persisted}

object SettingsScreen:
  def apply(app: AppState): Resource[IO, HtmlElement[IO]] =
    div(
      cls := Screen.frame,
      Screen.header("Settings", app.go(Route.Tonight)),
      // the forms copy the saved state, so they are rebuilt when an import or an undo replaces it
      div(cls := "flex flex-col gap-4", app.replaced.map(_ => app.persisted.get.toResource.flatMap(choices(app, _)))),
      Screen.section("Your data")(data(app)),
      button(cls := Wide, onClick --> (_.foreach(_ => app.go(Route.About))), "About the science"),
      Screen.makersPlate
    )

  private def choices(app: AppState, p0: Persisted): Resource[IO, HtmlElement[IO]] =
    div(
      cls := "flex flex-col gap-4",
      Screen.section("Profile")(
        p(cls := "text-sm text-dim", "Changing these re-estimates every night, past ones included."),
        ProfileFields(p0.profile, "Save profile", app.updateProfile)),
      Screen.section("Units")(
        choice("Blood alcohol unit", BacUnit.values.toList.map(u => u -> Display.unitName(u)), p0.settings.bacUnit,
          u => app.updateSettings(_.copy(bacUnit = u)))),
      Screen.section("Standard drink")(
        p(cls := "text-sm text-dim", "Used to count standard drinks in History."),
        choice("Standard drink", StandardDrink.values.toList.map(s => s -> Display.standardDrinkName(s)), p0.settings.standardDrink,
          s => app.updateSettings(_.copy(standardDrink = s))))
    )

  private val Wide = "min-h-12 w-full rounded-xl border border-line bg-surface px-3 font-semibold"

  private def data(app: AppState): Resource[IO, HtmlElement[IO]] =
    (SignallingRef[IO].of(List.empty[String]), SignallingRef[IO].of(false)).tupled.toResource.flatMap { (errors, confirming) =>
      div(
        cls := "space-y-3",
        p(cls := "text-sm text-dim", "Everything stays on this device. A backup is a file you keep; importing one replaces what is here."),
        button(cls := Wide,
          onClick --> (_.foreach(_ =>
            (app.persisted.get, Now.instant).flatMapN((p, now) =>
              Files.download(s"owl-hours-${LocalDateTime.format(now).take(10)}.json", Backup.encode(p, now))))),
          "Export all data"),
        label(cls := s"$Wide flex items-center justify-center",
          span("Import a backup"),
          input.withSelf { self =>
            (typ := "file", cls := "sr-only",
              onChange --> (_.foreach(_ => Files.read(self).flatMap {
                case None => IO.unit
                case Some(text) =>
                  Backup.decode(text) match
                    case Left(e)     => errors.set(List(e))
                    case Right(next) => errors.set(Nil) *> app.importBackup(next)
              })))
          }),
        Sheet.errors(errors),
        div(confirming.map {
          case false =>
            button(cls := "min-h-12 w-full rounded-xl border border-warn/60 bg-surface px-3 font-semibold text-warn",
              onClick --> (_.foreach(_ => confirming.set(true))), "Delete all data")
          case true =>
            div(cls := "space-y-3 rounded-xl border border-warn/60 bg-surface-2 p-3",
              p("This deletes your profile, settings and every night on this device. It can't be undone."),
              div(cls := "flex gap-2",
                button(cls := "min-h-12 flex-1 rounded-xl border border-line", onClick --> (_.foreach(_ => confirming.set(false))),
                  "Keep my data"),
                button(cls := "min-h-12 flex-1 rounded-xl border border-warn font-bold text-warn",
                  onClick --> (_.foreach(_ => app.deleteEverything)), "Delete everything")))
        })
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
