package owlhours.app.ui

import calico.html.io.{*, given}
import cats.Eq
import cats.data.EitherNel
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.concurrent.{Signal, SignallingRef}
import fs2.dom.HtmlElement
import java.time.Instant
import owlhours.app.{AppState, Route}
import owlhours.app.logic.{Amount, ChartSvg, Durations, Edits, WhatIf}
import owlhours.app.platform.{Now, TimeFormat}
import owlhours.catalogue.*
import owlhours.domain.*
import scala.concurrent.duration.DurationInt

object AddDrinkSheet:
  private final case class Pick(entry: Entry)

  /** Catalogue ids of drinks logged before, most recent first, as search boosts. */
  private def recents(sessions: Vector[Session]): Map[CatalogueId, Int] =
    sessions.flatMap(_.intakes).collect { case d: Intake.Drink => d }
      .sortBy(d => -d.start.toEpochMilli)
      .flatMap(_.snapshot.catalogueId)
      .distinct
      .zipWithIndex
      .map((id, i) => id -> (1000 - i))
      .toMap

  def apply(app: AppState, catalogue: Catalogue): Resource[IO, HtmlElement[IO]] =
    (SignallingRef[IO].of(""), SignallingRef[IO].of(Option.empty[Group]), SignallingRef[IO].of(Option.empty[Pick]))
      .tupled.toResource.flatMap { (query, group, pick) =>
        val results: Signal[IO, List[Entry]] = (query, group, app.persisted.map(p => recents(p.sessions))).mapN { (q, g, recent) =>
          val boost = (id: CatalogueId) => recent.getOrElse(id, 0)
          g match
            case Some(gr) if q.trim.isEmpty => Search.search("", catalogue.inGroup(gr), boost, limit = 200)
            case Some(gr)                   => Search.search(q, catalogue.inGroup(gr), boost)
            case None                       => Search.search(q, catalogue.all, boost)
        }
        // The detail panel is built once per picked drink; its parts follow `pick`, so the slider is never replaced mid-drag.
        val picked = pick.map(_.map(_.entry.id)).changes(using Eq.fromUniversalEquals)
        div(
          cls := "mx-auto flex min-h-dvh max-w-md flex-col p-4",
          div(cls := "flex items-center justify-between",
            h1(cls := "font-ui text-2xl font-semibold uppercase tracking-wide", "Add a drink"),
            button(cls := "min-h-12 px-3 text-dim", onClick --> (_.foreach(_ => app.go(Route.Tonight))), "Cancel")),
          input.withSelf { self =>
            (cls := "mt-3 min-h-12 w-full rounded-xl border border-line bg-surface px-3 text-lg",
              aria.label := "Search drinks", placeholder := "Search: lager, negroni, g&t…",
              onInput --> (_.foreach(_ => self.value.get.flatMap(query.set) *> pick.set(None))))
          },
          div(cls := "mt-3 flex flex-wrap gap-2", Group.values.toList.map { g =>
            Pickers.chip(g.label, group.map(_.contains(g)), group.update(cur => if cur.contains(g) then None else Some(g)) *> pick.set(None))
          }),
          div(picked.map {
            case None => resultList(results, pick)
            case Some(_) =>
              pick.get.toResource.flatMap {
                case Some(start) => detail(app, start.entry, pick)
                case None        => div(())
              }
          })
        )
      }

  private def resultList(results: Signal[IO, List[Entry]], pick: SignallingRef[IO, Option[Pick]]) =
    ul(
      cls := "mt-3 space-y-2",
      children <-- results.map(_.map { e =>
        li(button(
          cls := "flex min-h-12 w-full items-center justify-between rounded-xl border border-line bg-surface px-3 text-left",
          onClick --> (_.foreach(_ => pick.set(Some(Pick(e))))),
          span(e.name),
          span(cls := "text-sm text-dim", e.category.group.label)
        ))
      })
    )

  /** Recipes: the ×1 volume and the amount presets as (label, ml text, strength). */
  private def recipePresets(r: Recipe): List[(String, String, Double)] =
    Resolve(Choice.OfRecipe(r)).toOption.toList.flatMap { base =>
      List(1.0 -> "×1", 1.5 -> "×1.5", 2.0 -> "×2").map((k, t) => (t, ml(base.volume.unwrap * k), k))
    }

  private def ml(x: Double): String = Format.number(math.round(x * 10) / 10.0)

  private def detail(app: AppState, entry: Entry, pick: SignallingRef[IO, Option[Pick]]): Resource[IO, HtmlElement[IO]] =
    val presets: List[(String, String)] = entry match
      case i: Ingredient => i.servings.map(sv => sv.display -> ml(sv.volume.unwrap))
      case r: Recipe     => recipePresets(r).map((t, v, _) => t -> v)
    val amount0   = presets.headOption.fold("")(_._2)
    val duration0 = Amount.ml(amount0).toOption.fold("15")(v => Format.number(Durations.default(entry, v).unwrap))
    (
      SignallingRef[IO].of(amount0),
      SignallingRef[IO].of(duration0),
      SignallingRef[IO].of[Pickers.When](Pickers.When.Ago(0)),
      SignallingRef[IO].of(Option.empty[Double]),
      SignallingRef[IO].of(List.empty[String]),
      SignallingRef[IO].of(Option.empty[(String, List[String])])
    ).tupled.toResource.flatMap { (amount, duration, when, abv, errors, preview) =>
      val adjust: Resource[IO, HtmlElement[IO]] = entry match
        case s: Style =>
          div(
            label(cls := "block font-ui uppercase tracking-wider text-dim",
              abv.map(a => s"ABV ${Format.number(a.getOrElse(s.abv.unwrap))} %")),
            input.withSelf { self =>
              (typ := "range", aria.label := "ABV", cls := "mt-2 w-full accent-pink",
                minAttr := Format.number(s.abvMin.unwrap), maxAttr := Format.number(s.abvMax.unwrap), stepAttr := "0.1",
                value := Format.number(s.abv.unwrap),
                onInput --> (_.foreach(_ => self.value.get.flatMap(v => abv.set(v.toDoubleOption)))))
            }
          )
        case _ => div(())
      val submit =
        (amount.get, duration.get, when.get, abv.get).flatMapN((a, d, w, x) => add(app, entry, x, a, d, w, errors))
      // Recomputed after 300 ms of quiet; cancelled with the sheet. Invalid input clears the preview.
      val previewLoop: Resource[IO, Unit] =
        (amount: Signal[IO, String], duration: Signal[IO, String], when: Signal[IO, Pickers.When], abv: Signal[IO, Option[Double]])
          .tupled.discrete
          .debounce(300.millis)
          .evalMap { (a, d, w, x) =>
            (app.persisted.get, Now.instant).flatMapN { (p, now) =>
              preview.set(for
                profile <- p.profile
                drink   <- candidate(entry, x, a, d, w, now).toOption
              yield
                val wi = WhatIf.of(profile, p.sessions, drink, now)
                (ChartSvg.render(wi.tonight, now, wi.markers, TimeFormat.utcOffsetMinutes, TimeFormat.hhmm, wi.ghost),
                  WhatIf.describe(wi, p.settings.bacUnit, TimeFormat.hhmm)))
            }
          }
          .compile.drain.background.void
      previewLoop *> div(
        cls := "mt-4 space-y-4",
        h2(cls := "font-display text-4xl text-ice", entry.name),
        adjust,
        Pickers.amountField("Amount (ml)", amount, presets),
        Pickers.when(when),
        Pickers.minutesField(duration),
        div(dataAttr("testid") := "what-if", cls := "rounded-2xl border border-line bg-surface p-2",
          p(cls := "px-2 font-ui text-sm uppercase tracking-widest text-dim", "With this drink"),
          div(InnerHtml(preview.map(_.fold("")(_._1)))),
          ul(cls := "space-y-1 px-2 pb-1 font-ui text-lg tabular-nums",
            children <-- preview.map(_.toList.flatMap(_._2).map(l => li(l))))),
        Sheet.errors(errors),
        div(cls := "flex gap-2",
          button(cls := "min-h-14 flex-1 rounded-2xl border border-line bg-surface text-lg uppercase",
            onClick --> (_.foreach(_ => pick.set(None))), "Back"),
          button(cls := "min-h-14 flex-[2] rounded-2xl border border-pink bg-surface-2 text-xl font-bold uppercase text-pink",
            onClick --> (_.foreach(_ => submit)), "Add"))
      )
    }

  private def candidate(entry: Entry, abv: Option[Double], amount: String, duration: String, when: Pickers.When,
      now: Instant): EitherNel[String, Intake.Drink] =
    val volume  = Amount.drinkMl(amount).toEitherNel
    val minutes = Amount.minutes(duration).toEitherNel
    val start   = Pickers.resolveWhen(when, now).toEitherNel
    val snapshot: EitherNel[String, DrinkSnapshot] = volume.flatMap { v =>
      (entry match
        case s: Style   => Abv.from(abv.getOrElse(s.abv.unwrap)).flatMap(a => Resolve(Choice.OfStyle(s, a, v)))
        case p: Product => Resolve(Choice.OfProduct(p, v))
        case r: Recipe  =>
          // A preset amount resolves at that strength exactly; any other amount scales the ×1 recipe proportionally.
          recipePresets(r).find(_._2 == amount.trim) match
            case Some((_, _, k)) => Resolve(Choice.OfRecipe(r, strength = k))
            case None            => Resolve(Choice.OfRecipe(r)).map(Edits.rescale(_, v))
      ).toEitherNel
    }
    (snapshot, start, minutes).parMapN((s, t, d) => Intake.Drink(s, t, d))

  private def add(app: AppState, entry: Entry, abv: Option[Double], amount: String, duration: String, when: Pickers.When,
      errors: SignallingRef[IO, List[String]]): IO[Unit] =
    Now.instant.flatMap { now =>
      candidate(entry, abv, amount, duration, when, now) match
        case Left(es)     => errors.set(es.toList)
        case Right(drink) => errors.set(Nil) *> app.addIntake(drink) *> app.go(Route.Tonight)
    }
