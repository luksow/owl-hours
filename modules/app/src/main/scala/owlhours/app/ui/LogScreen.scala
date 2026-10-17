package owlhours.app.ui

import calico.html.io.{*, given}
import cats.Eq
import cats.data.EitherNel
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.concurrent.SignallingRef
import fs2.dom.HtmlElement
import owlhours.app.{AppState, CatalogueState, Route}
import owlhours.app.logic.{Amount, Display, Edits, IntakeEdit}
import owlhours.app.platform.{LocalDateTime, Now, TimeFormat}
import owlhours.catalogue.Catalogue
import owlhours.domain.*
import owlhours.app.logic.WhenPick.When

object LogScreen:
  private final case class Ref(night: Int, index: Int)

  def apply(app: AppState): Resource[IO, HtmlElement[IO]] =
    (SignallingRef[IO].of(Option.empty[Ref]), app.catalogue.get).tupled.toResource.flatMap { (editing, state) =>
      val catalogue = state match
        case CatalogueState.Ready(c) => Some(c)
        case _                       => None
      div(
        cls := "mx-auto flex min-h-dvh max-w-md flex-col gap-4 p-4",
        div(cls := "flex items-center justify-between",
          h1(cls := "font-ui text-2xl font-semibold uppercase tracking-wide", "Log"),
          button(cls := "min-h-12 px-3 text-dim", onClick --> (_.foreach(_ => app.go(Route.Tonight))), "Done")),
        // rebuilt only when the nights change; opening an editor re-renders just that row's slot
        div(app.persisted.map(_.sessions).changes(using Eq.fromUniversalEquals).map { sessions =>
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
                        span(s"${TimeFormat.hhmm(intake.startsAt)} · ${Display.intake(intake)}")
                      ),
                      div(editing.map(_.contains(Ref(n, i))).changes(using Eq.fromUniversalEquals).map {
                        case true  => editor(app, catalogue, n, i, intake, editing)
                        case false => div(())
                      })
                    )
                  })
                )
              }
            )
        })
      )
    }


  private def editor(app: AppState, catalogue: Option[Catalogue], n: Int, i: Int, intake: Intake,
      editing: SignallingRef[IO, Option[Ref]]) =
    val abvLimits = intake match
      case Intake.Drink(s, _, _) => Edits.abvRange(s.catalogueId.flatMap(id => catalogue.flatMap(_.get(id)))).map((s.name, _))
      case _                     => None
    val (volume0, duration0, abv0, kcal0) = intake match
      case Intake.Drink(s, _, d) =>
        (Some(s.volume.unwrap), Some(d.unwrap), Option.when(s.components.isEmpty && abvLimits.isDefined)(s.abv.unwrap), None)
      case Intake.Water(v, _, d) => (Some(v.unwrap), Some(d.unwrap), None, None)
      case Intake.Meal(k, _)     => (None, None, None, Some(k.unwrap))
    (
      SignallingRef[IO].of[When](When.At(LocalDateTime.format(intake.startsAt))),
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
            Pickers.resolveWhen(st, now).toEitherNel,
            volume0.traverse(_ => Amount.drinkMl(v)).toEitherNel,
            duration0.traverse(_ => Amount.minutes(d)).toEitherNel,
            abv0.traverse(_ => Amount.abv(a).flatMap(x => abvLimits.fold(Right(x))((name, r) => Edits.checkAbv(x, r, name)))).toEitherNel,
            kcal0.traverse(_ => Amount.kcal(k)).toEitherNel
          ).parMapN((start, volume, duration, abv, kcal) => IntakeEdit(start, duration, volume, abv, kcal))
          edit.flatMap(e => Edits(intake, e).toEitherNel) match
            case Left(es)       => errors.set(es.toList)
            case Right(updated) => editing.set(None) *> app.replaceIntake(intake, updated)
        }
      div(
        cls := "mt-2 space-y-3 rounded-xl border border-line bg-surface-2 p-3",
        Pickers.when(start, app.now),
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
