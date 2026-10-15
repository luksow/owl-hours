package owlhours.app.ui

import calico.html.io.{*, given}
import cats.data.EitherNel
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.concurrent.SignallingRef
import fs2.dom.HtmlElement
import owlhours.app.{AppState, Route}
import owlhours.app.logic.{Amount, Edits, IntakeEdit}
import owlhours.app.platform.{LocalDateTime, Now, TimeFormat}
import owlhours.domain.*

object LogScreen:
  private final case class Ref(night: Int, index: Int)

  def apply(app: AppState): Resource[IO, HtmlElement[IO]] =
    SignallingRef[IO].of(Option.empty[Ref]).toResource.flatMap { editing =>
      div(
        cls := "mx-auto flex min-h-dvh max-w-md flex-col gap-4 p-4",
        div(cls := "flex items-center justify-between",
          h1(cls := "font-ui text-2xl font-semibold uppercase tracking-wide", "Log"),
          button(cls := "min-h-12 px-3 text-dim", onClick --> (_.foreach(_ => app.go(Route.Tonight))), "Done")),
        div((app.persisted.map(_.sessions), editing).mapN { (sessions, open) =>
          if sessions.isEmpty then p(cls := "text-dim", "Nothing logged yet.")
          else
            div(
              cls := "space-y-6",
              sessions.zipWithIndex.reverse.toList.map { (night, n) =>
                div(
                  h2(cls := "font-ui text-lg uppercase tracking-widest text-amber", LocalDateTime.dayLabel(night.startedAt)),
                  ul(cls := "mt-2 space-y-2", night.intakes.zipWithIndex.toList.map { (intake, i) =>
                    li(
                      button(
                        cls := "flex min-h-12 w-full items-center justify-between rounded-xl border border-line bg-surface px-3 text-left",
                        onClick --> (_.foreach(_ => editing.update(cur => if cur.contains(Ref(n, i)) then None else Some(Ref(n, i))))),
                        span(s"${TimeFormat.hhmm(intake.startsAt)} · ${describe(intake)}")
                      ),
                      Option.when(open.contains(Ref(n, i)))(editor(app, n, i, intake, editing))
                    )
                  })
                )
              }
            )
        })
      )
    }

  private def describe(i: Intake): String = i match
    case Intake.Drink(s, _, d) => s"${s.name} · ${Format.number(s.volume.unwrap)} ml · ${Format.number(s.abv.unwrap)} % · ${Format.number(d.unwrap)} min"
    case Intake.Water(v, _, d) => s"Water · ${Format.number(v.unwrap)} ml · ${Format.number(d.unwrap)} min"
    case Intake.Meal(k, _)     => s"Food · ${Format.number(k.unwrap)} kcal"

  private def editor(app: AppState, n: Int, i: Int, intake: Intake, editing: SignallingRef[IO, Option[Ref]]) =
    val (volume0, duration0, abv0, kcal0) = intake match
      case Intake.Drink(s, _, d) =>
        (Some(s.volume.unwrap), Some(d.unwrap), Option.when(s.components.isEmpty)(s.abv.unwrap), None)
      case Intake.Water(v, _, d) => (Some(v.unwrap), Some(d.unwrap), None, None)
      case Intake.Meal(k, _)     => (None, None, None, Some(k.unwrap))
    (
      SignallingRef[IO].of(LocalDateTime.format(intake.startsAt)),
      SignallingRef[IO].of(volume0.fold("")(Format.number)),
      SignallingRef[IO].of(duration0.fold("")(Format.number)),
      SignallingRef[IO].of(abv0.fold("")(Format.number)),
      SignallingRef[IO].of(kcal0.fold("")(Format.number)),
      SignallingRef[IO].of(List.empty[String])
    ).tupled.toResource.flatMap { (start, volume, duration, abv, kcal, errors) =>
      def text(caption: String, ref: SignallingRef[IO, String], tpe: String) =
        label(cls := "block",
          span(cls := "block font-ui text-sm uppercase tracking-wider text-dim", caption),
          input.withSelf { self =>
            (typ := tpe, cls := "mt-1 min-h-12 w-full rounded-xl border border-line bg-wall px-3 text-lg", value <-- ref,
              onInput --> (_.foreach(_ => self.value.get.flatMap(ref.set))))
          })
      val save: IO[Unit] =
        (start.get, volume.get, duration.get, abv.get, kcal.get, Now.instant).flatMapN { (st, v, d, a, k, now) =>
          val edit: EitherNel[String, IntakeEdit] = (
            LocalDateTime.parse(st).toRight("Pick a date and time").flatMap(Edits.notInFuture(_, now)).toEitherNel,
            volume0.traverse(_ => Amount.drinkMl(v)).toEitherNel,
            duration0.traverse(_ => Amount.minutes(d)).toEitherNel,
            abv0.traverse(_ => Amount.abv(a)).toEitherNel,
            kcal0.traverse(_ => Amount.kcal(k)).toEitherNel
          ).parMapN((start, volume, duration, abv, kcal) => IntakeEdit(start, duration, volume, abv, kcal))
          edit.flatMap(e => Edits(intake, e).toEitherNel) match
            case Left(es)       => errors.set(es.toList)
            case Right(updated) => editing.set(None) *> app.replaceIntake(intake, updated)
        }
      div(
        cls := "mt-2 space-y-3 rounded-xl border border-line bg-surface-2 p-3",
        text("Started at", start, "datetime-local"),
        Option.when(volume0.isDefined)(text("Amount (ml)", volume, "text")),
        Option.when(duration0.isDefined)(text("Drinking time (min)", duration, "text")),
        Option.when(abv0.isDefined)(text("ABV (%)", abv, "text")),
        Option.when(kcal0.isDefined)(text("Energy (kcal)", kcal, "text")),
        Sheet.errors(errors),
        div(cls := "flex gap-2",
          button(cls := "min-h-12 flex-1 rounded-xl border border-warn/60 text-warn",
            onClick --> (_.foreach(_ => editing.set(None) *> app.deleteIntake(intake))), "Delete"),
          button(cls := "min-h-12 flex-[2] rounded-xl border border-pink bg-surface text-pink font-bold uppercase",
            onClick --> (_.foreach(_ => save)), "Save"))
      )
    }
