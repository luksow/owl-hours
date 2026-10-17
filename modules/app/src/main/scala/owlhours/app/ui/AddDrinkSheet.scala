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
import owlhours.app.logic.{Amount, ChartSvg, Display, Durations, Edits, RecipeEdit, Recompute, WhatIf}
import owlhours.app.platform.{Now, TimeFormat}
import owlhours.catalogue.*
import owlhours.domain.*
import owlhours.app.logic.WhenPick.When
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
      List(1.0 -> "×1", 1.5 -> "×1.5", 2.0 -> "×2").map((k, t) => (t, ml(base.poured.unwrap * k), k))
    }

  private def ml(x: Double): String = Format.number(math.round(x * 10) / 10.0)

  private def detail(app: AppState, entry: Entry, pick: SignallingRef[IO, Option[Pick]]): Resource[IO, HtmlElement[IO]] =
    val presets: List[(String, String)] = entry match
      case i: Ingredient => i.servings.map(sv => sv.display -> ml(sv.volume.unwrap))
      case r: Recipe     => recipePresets(r).map((t, v, _) => t -> v)
    val lines     = entry match
      case r: Recipe => r.lines
      case _         => Nil
    val amount0   = presets.headOption.fold("")(_._2)
    val duration0 = Amount.ml(amount0).toOption.fold("15")(v => Format.number(Durations.default(entry, v).unwrap))
    (
      SignallingRef[IO].of(amount0),
      SignallingRef[IO].of(duration0),
      SignallingRef[IO].of[When](When.Ago(0)),
      SignallingRef[IO].of(Option.empty[Double]),
      SignallingRef[IO].of(List.empty[String]),
      SignallingRef[IO].of(Option.empty[(String, List[String])]),
      SignallingRef[IO].of(false),
      lines.traverse(_ => SignallingRef[IO].of("")),
      SignallingRef[IO].of(duration0)
    ).tupled.toResource.flatMap { (amount, duration, when, abv, errors, preview, perIngredient, ingredients, lastDefault) =>
      // the drinking time follows the size until the person changes it
      val followSize: Resource[IO, Unit] =
        amount.discrete.changes.evalMap { a =>
          Amount.drinkMl(a).toOption.fold(IO.unit) { v =>
            val d = Format.number(Durations.default(entry, v).unwrap)
            (duration.get, lastDefault.get).flatMapN((cur, last) => IO.whenA(cur == last)(duration.set(d))) *> lastDefault.set(d)
          }
        }.compile.drain.background.void
      val readIngredients: IO[Option[List[String]]] =
        perIngredient.get.ifM(ingredients.traverse(_.get).map(Some(_)), IO.pure(None))
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
        (amount.get, duration.get, when.get, abv.get, readIngredients).flatMapN((a, d, w, x, ing) => add(app, entry, x, a, d, w, ing, errors))
      // At most once per 300 ms, with the latest input; cancelled with the sheet. Invalid input or a failure clears it.
      val previewLoop: Resource[IO, Unit] =
        Recompute.latest(
          (List(amount.discrete.void, duration.discrete.void, when.discrete.void, abv.discrete.void, perIngredient.discrete.void) ++
            ingredients.map(_.discrete.void)).reduce(_ merge _),
          300.millis
        ) {
          (app.persisted.get, Now.instant, amount.get, duration.get, when.get, abv.get, readIngredients).mapN { (p, now, a, d, w, x, ing) =>
            for
              profile <- p.profile
              drink   <- candidate(entry, x, a, d, w, ing, now).toOption
            yield
              val wi = WhatIf.of(profile, p.sessions, drink, now)
              (ChartSvg.render(wi.tonight, now, wi.markers, TimeFormat.utcOffsetMinutes, TimeFormat.hhmm, wi.ghost,
                unit = p.settings.bacUnit, label = "Blood alcohol estimate with this drink"),
                WhatIf.describe(wi, p.settings.bacUnit, TimeFormat.hhmm))
          }
        }.foreach(preview.set).compile.drain.background.void
      followSize *> previewLoop *> div(
        cls := "mt-4 space-y-4",
        h2(cls := "font-display text-4xl text-ice", entry.name),
        adjust,
        entry match
          case r: Recipe =>
            div(
              div(perIngredient.map {
                case false => Pickers.amountField("Amount (ml)", amount, presets)
                case true =>
                  div(cls := "space-y-2", r.lines.zip(ingredients).map { (line, ref) =>
                    label(cls := "block",
                      span(cls := "block font-ui text-sm uppercase tracking-wider text-dim", s"${line.ingredient.name} (ml)"),
                      input.withSelf { self =>
                        (typ := "text", inputMode := "decimal", value <-- ref,
                          cls := "mt-1 min-h-12 w-full rounded-xl border border-line bg-surface px-3 text-lg text-ink",
                          onInput --> (_.foreach(_ => self.value.get.flatMap(ref.set))))
                      })
                  })
              }),
              meltNote(r, amount, perIngredient, ingredients), // under the amount (spec §9)
              button(cls := "mt-2 min-h-12 rounded-xl border border-line bg-surface px-3",
                aria.expanded <-- perIngredient,
                onClick --> (_.foreach(_ =>
                  perIngredient.get.flatMap {
                    case true => perIngredient.set(false)
                    case false =>
                      // start from the amount chosen so far, scaled from the ×1 recipe
                      amount.get.flatMap { a =>
                        val base  = recipePresets(r).headOption.flatMap(_._2.toDoubleOption).getOrElse(1.0)
                        val scale = Amount.drinkMl(a).toOption.fold(1.0)(_.unwrap / base)
                        RecipeEdit.defaults(r, scale).zip(ingredients).traverse_((v, ref) => ref.set(v))
                      } *> perIngredient.set(true)
                  })),
                "Adjust ingredients")
            )
          case _ => Pickers.amountField("Amount (ml)", amount, presets)
        ,
        Pickers.when(when, app.now),
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

  /** A preset amount resolves at that strength exactly; any other amount scales the ×1 recipe so it pours `v`. */
  private def recipeByAmount(r: Recipe, amount: String, v: Millilitres): Either[String, DrinkSnapshot] =
    recipePresets(r).find(_._2 == amount.trim) match
      case Some((_, _, k)) => Resolve(Choice.OfRecipe(r, strength = k))
      case None            => Resolve(Choice.OfRecipe(r)).map(s => Edits.rescalePoured(s, v).copy(name = s"${r.name} (custom)"))

  private def methodText(m: Method): String = m match
    case Method.Built   => "built over ice"
    case Method.Stirred => "stirred with ice"
    case Method.Shaken  => "shaken with ice"
    case Method.Blended => "blended with ice"
    case Method.Neat    => "served without ice"

  /** Spec §9: what the ice adds, under the amount; read-only, and nothing for drinks without melt water. */
  private def meltNote(r: Recipe, amount: Signal[IO, String], perIngredient: Signal[IO, Boolean],
      ingredients: List[Signal[IO, String]]): Resource[IO, HtmlElement[IO]] =
    val note = (amount, perIngredient, ingredients.sequence).mapN { (a, per, ing) =>
      val snapshot =
        if per then RecipeEdit.resolve(r, ing).toOption
        else Amount.drinkMl(a).toOption.flatMap(v => recipeByAmount(r, a, v).toOption)
      snapshot.flatMap(Display.meltNote(_, methodText(r.method))).getOrElse("")
    }
    p(cls := "mt-1 text-sm text-dim", note)

  /** `ingredients`: per-ingredient amounts when the person adjusted a cocktail line by line (the amount is then ignored). */
  private def candidate(entry: Entry, abv: Option[Double], amount: String, duration: String, when: When,
      ingredients: Option[List[String]], now: Instant): EitherNel[String, Intake.Drink] =
    val volume  = Amount.drinkMl(amount).toEitherNel
    val minutes = Amount.minutes(duration).toEitherNel
    val start   = Pickers.resolveWhen(when, now).toEitherNel
    def byAmount: EitherNel[String, DrinkSnapshot] = volume.flatMap { v =>
      (entry match
        case s: Style   => Abv.from(abv.getOrElse(s.abv.unwrap)).flatMap(a => Resolve(Choice.OfStyle(s, a, v)))
        case p: Product => Resolve(Choice.OfProduct(p, v))
        case r: Recipe  => recipeByAmount(r, amount, v)
      ).toEitherNel
    }
    val snapshot: EitherNel[String, DrinkSnapshot] = (entry, ingredients) match
      case (r: Recipe, Some(texts)) => RecipeEdit.resolve(r, texts)
      case _                        => byAmount
    (snapshot, start, minutes).parMapN((s, t, d) => Intake.Drink(s, t, d))

  private def add(app: AppState, entry: Entry, abv: Option[Double], amount: String, duration: String, when: When,
      ingredients: Option[List[String]], errors: SignallingRef[IO, List[String]]): IO[Unit] =
    Now.instant.flatMap { now =>
      candidate(entry, abv, amount, duration, when, ingredients, now) match
        case Left(es)     => errors.set(es.toList)
        case Right(drink) => errors.set(Nil) *> app.addIntake(drink) *> app.go(Route.Tonight)
    }
