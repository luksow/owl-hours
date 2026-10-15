package owlhours.app.ui

import calico.html.io.{*, given}
import cats.Eq
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.concurrent.{Signal, SignallingRef}
import fs2.dom.HtmlElement
import owlhours.app.{AppState, Route}
import owlhours.app.logic.Durations
import owlhours.app.platform.Now
import owlhours.catalogue.*
import owlhours.domain.*

object AddDrinkSheet:
  private final case class Pick(
      entry: Entry,
      serving: Option[Serving],
      abv: Option[Double],
      strength: Double,
      minutesAgo: Int,
      error: Option[String]
  )

  private val Ago = List(0 -> "Now", 15 -> "15 min ago", 30 -> "30 min ago", 60 -> "60 min ago")

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
            chip(g.label, group.map(_.contains(g)), group.update(cur => if cur.contains(g) then None else Some(g)) *> pick.set(None))
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
          onClick --> (_.foreach(_ => pick.set(Some(initial(e))))),
          span(e.name),
          span(cls := "text-sm text-dim", e.category.group.label)
        ))
      })
    )

  private def initial(e: Entry): Pick = e match
    case s: Style   => Pick(s, s.servings.headOption, Some(s.abv.unwrap), 1.0, 0, None)
    case p: Product => Pick(p, p.servings.headOption, None, 1.0, 0, None)
    case r: Recipe  => Pick(r, None, None, 1.0, 0, None)

  private val chipOn  = List("min-h-12", "rounded-xl", "border", "border-pink", "bg-surface-2", "px-3", "text-pink")
  private val chipOff = List("min-h-12", "rounded-xl", "border", "border-line", "bg-surface", "px-3")

  private def chip(text: String, on: Signal[IO, Boolean], action: IO[Unit]) =
    button(
      cls <-- on.map(if _ then chipOn else chipOff),
      aria.pressed <-- on.map(_.toString),
      onClick --> (_.foreach(_ => action)),
      text
    )

  private def detail(app: AppState, entry: Entry, pick: SignallingRef[IO, Option[Pick]]): Resource[IO, HtmlElement[IO]] =
    def set(f: Pick => Pick) = pick.update(_.map(f))
    val current = pick.map(_.getOrElse(initial(entry)))
    val servings = entry match
      case i: Ingredient => i.servings
      case _: Recipe     => Nil
    val adjust: Resource[IO, HtmlElement[IO]] = entry match
      case s: Style =>
        div(
          label(cls := "block font-ui uppercase tracking-wider text-dim",
            current.map(pk => s"ABV ${Format.number(pk.abv.getOrElse(s.abv.unwrap))} %")),
          input.withSelf { self =>
            (typ := "range", aria.label := "ABV", cls := "mt-2 w-full accent-pink",
              minAttr := Format.number(s.abvMin.unwrap), maxAttr := Format.number(s.abvMax.unwrap), stepAttr := "0.1",
              value := Format.number(s.abv.unwrap),
              onInput --> (_.foreach(_ => self.value.get.flatMap(v => set(_.copy(abv = v.toDoubleOption))))))
          }
        )
      case _: Recipe =>
        div(cls := "flex gap-2", List(1.0 -> "×1", 1.5 -> "×1.5", 2.0 -> "×2").map { (k, t) =>
          chip(t, current.map(_.strength == k), set(_.copy(strength = k)))
        })
      case _ => div(())
    div(
      cls := "mt-4 space-y-4",
      h2(cls := "font-display text-4xl text-ice", entry.name),
      adjust,
      div(cls := "flex flex-wrap gap-2", servings.map(sv => chip(sv.display, current.map(_.serving.contains(sv)), set(_.copy(serving = Some(sv)))))),
      div(cls := "flex flex-wrap gap-2", Ago.map((m, t) => chip(t, current.map(_.minutesAgo == m), set(_.copy(minutesAgo = m))))),
      p(cls := "text-warn", current.map(_.error.getOrElse(""))),
      div(cls := "flex gap-2",
        button(cls := "min-h-14 flex-1 rounded-2xl border border-line bg-surface text-lg uppercase",
          onClick --> (_.foreach(_ => pick.set(None))), "Back"),
        button(cls := "min-h-14 flex-[2] rounded-2xl border border-pink bg-surface-2 text-xl font-bold uppercase text-pink",
          onClick --> (_.foreach(_ => current.get.flatMap(add(app, _, pick)))), "Add"))
    )

  private def add(app: AppState, pk: Pick, pick: SignallingRef[IO, Option[Pick]]): IO[Unit] =
    val choice: Either[String, Choice] = pk.entry match
      case s: Style =>
        for
          sv  <- pk.serving.toRight("Choose a size")
          abv <- Abv.from(pk.abv.getOrElse(s.abv.unwrap))
        yield Choice.OfStyle(s, abv, sv.volume)
      case pr: Product => pk.serving.toRight("Choose a size").map(sv => Choice.OfProduct(pr, sv.volume))
      case r: Recipe   => Right(Choice.OfRecipe(r, strength = pk.strength))
    choice.flatMap(Resolve(_)) match
      case Left(e) => pick.update(_.map(_.copy(error = Some(e))))
      case Right(snapshot) =>
        Now.instant.flatMap { now =>
          val start = now.minusSeconds(pk.minutesAgo * 60L)
          app.addIntake(Intake.Drink(snapshot, start, Durations.default(pk.entry, snapshot.volume)))
        } *> app.go(Route.Tonight)
