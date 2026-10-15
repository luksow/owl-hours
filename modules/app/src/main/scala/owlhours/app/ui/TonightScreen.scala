package owlhours.app.ui

import calico.html.io.{*, given}
import cats.Eq
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.concurrent.Signal
import fs2.dom.HtmlElement
import java.time.Instant
import owlhours.app.{AppState, Route}
import owlhours.app.logic.*
import owlhours.app.platform.{Now, TimeFormat}
import owlhours.catalogue.Catalogue
import owlhours.domain.*
import owlhours.model.{Impairment, Trend}

object TonightScreen:

  /** Everything that needs a simulation, recomputed only when its inputs change (spec §7.4). */
  private final case class Bundle(session: Option[Session], unit: BacUnit, projected: Option[Projected])
  private final case class Key(profile: Option[Profile], session: Option[Session], unit: BacUnit, hour: Long)

  /** A derived signal evaluated once per distinct input, shared by all subscribers. */
  private def shared[A, B](sig: Signal[IO, A])(f: A => B): Resource[IO, Signal[IO, B]] =
    sig.get.toResource.flatMap(a0 => sig.discrete.changes(using Eq.fromUniversalEquals).map(f).holdResource(f(a0)))

  def apply(app: AppState, catalogue: Catalogue): Resource[IO, HtmlElement[IO]] =
    for
      // closesAt runs a 24 h simulation: once per saved change, not per tick
      night <- shared(app.persisted) { p =>
        (p.profile, p.profile.flatMap(prof => p.sessions.lastOption.filter(_.intakes.nonEmpty).map(s => (s, Sessions.closesAt(prof, s)))),
          p.settings.bacUnit)
      }
      key = (night, app.now).mapN { case ((profile, last, unit), now) =>
        val session = last.collect { case (s, closes) if !now.isAfter(closes) => s }
        Key(profile, session, unit, if session.isEmpty then now.getEpochSecond / 3600 else 0L)
      }
      bundle <- shared(key) { k =>
        Bundle(k.session, k.unit, k.profile.map(p => Tonight.project(p, k.session, Instant.ofEpochSecond(k.hour * 3600 + 1800))))
      }
      view = (bundle, app.now).mapN((b, now) => b.projected.map(pr => (Tonight.at(pr, now), b.session, b.unit, now)))
      // Watt changes only with the state, so its animation is not restarted by the 30-second clock tick.
      watt = view.map(_.fold(Impairment.Sober)(_._1.impairment)).changes(using Eq.fromUniversalEquals).map(WattSvg.render)
      screen <- div(
        cls := "mx-auto flex min-h-dvh max-w-md flex-col",
        headerTag(cls := "flex items-center justify-between px-5 pt-5",
          span(cls := "font-display text-4xl text-pink neon", "Owl Hours"),
          div(cls := "flex items-center gap-3",
            span(cls := "text-sm uppercase tracking-widest text-dim", "Tonight"),
            button(cls := "min-h-12 rounded-xl border border-line px-3 text-sm uppercase tracking-wider",
              onClick --> (_.foreach(_ => app.go(Route.Log))), "Log"))),
        div(dataAttr("testid") := "watt", cls := "mx-auto mt-4 w-40 overflow-hidden rounded-2xl border border-line",
          InnerHtml(watt)),
        div(view.map {
          case None => div(())
          case Some((t, s, unit, now)) => body(t, s, unit, now)
        }),
        p(cls := "mt-3 px-5 text-center text-xs uppercase tracking-wider text-dim", "Estimate only · never a green light to drive"),
        actionBar(app, bundle.map(_.session))
      )
    yield screen

  private def body(t: Tonight, s: Option[Session], unit: BacUnit, now: Instant): Resource[IO, HtmlElement[IO]] =
    val current = t.projection.curve.samples.findLast(!_.at.isAfter(now)).getOrElse(t.projection.curve.samples.head)
    val trend = t.summary.trend match
      case Trend.Rising  => "↗ rising"
      case Trend.Falling => "↘ falling"
      case Trend.Steady  => "→ steady"
    val chart = ChartSvg.render(t, now, Marker.of(s.toList.flatMap(_.intakes)), TimeFormat.utcOffsetMinutes, TimeFormat.hhmm)
    div(
      cls := "px-5",
      div(cls := "mt-6 flex items-end gap-4",
        span(dataAttr("testid") := "bac", cls := "font-ui text-7xl font-bold leading-none tabular-nums",
          Display.bac(current.bacMgDl, current.bracMgL, unit)),
        div(cls := "pb-2",
          div(cls := "font-ui text-sm uppercase tracking-widest text-amber", trend),
          div(cls := "font-display text-3xl text-ice", Display.impairment(t.impairment)))),
      div(cls := "mt-4 rounded-2xl border border-line bg-surface p-2", InnerHtml(fs2.concurrent.Signal.constant(chart))),
      div(cls := "mt-3 flex gap-2",
        t.summary.peak.map(pk =>
          div(cls := "flex-1 rounded-xl border border-line bg-surface px-3 py-2",
            s"Peak ${Display.peak(pk.bacMgDl, unit)} at ${TimeFormat.hhmm(pk.at)}")),
        t.summary.backToZeroBy.map(b =>
          div(cls := "flex-1 rounded-xl border border-line bg-surface px-3 py-2", s"Back to ~0 by ${TimeFormat.hhmm(b)}"))),
      Option.when(t.impairment == Impairment.TakeCare)(takeCare)
    )

  private def takeCare: Resource[IO, HtmlElement[IO]] =
    div(
      cls := "mt-4 rounded-2xl border border-care/60 bg-surface p-4 font-body",
      h2(cls := "font-ui text-2xl font-semibold text-care", "Let's slow everything down."),
      p(cls := "mt-2", "Your estimate is above 3‰, where alcohol poisoning is a real risk. Stay with people you trust and sip water."),
      p(cls := "mt-2", "If someone can't be woken, is breathing slowly or irregularly, is cold or pale, or is vomiting while drowsy, call emergency services now (112 in Europe, 911 in the US).")
    )

  private def actionBar(app: AppState, session: Signal[IO, Option[Session]]) =
    val lastDrink = session.map(_.flatMap(_.lastDrink))
    div(
      cls := "sticky bottom-0 mt-auto grid grid-cols-2 gap-2 bg-wall/95 p-4 pb-[max(1rem,env(safe-area-inset-bottom))]",
      button(cls := "col-span-2 min-h-16 rounded-2xl border border-pink bg-surface-2 text-2xl font-bold uppercase tracking-wider text-pink",
        onClick --> (_.foreach(_ => app.go(Route.AddDrink))), "+ Drink"),
      button(cls := "min-h-14 rounded-2xl border border-line bg-surface text-lg uppercase disabled:opacity-40",
        disabled <-- lastDrink.map(_.isEmpty),
        onClick --> (_.foreach(_ =>
          (lastDrink.get, Now.instant).flatMapN {
            case (Some(d), now) => app.addIntake(Intake.Drink(d.snapshot, now, d.duration))
            case (None, _)      => IO.unit
          })),
        "Same again"),
      div(cls := "grid grid-cols-2 gap-2",
        button(cls := "min-h-14 rounded-2xl border border-ice/60 bg-surface text-lg uppercase text-ice",
          onClick --> (_.foreach(_ => app.go(Route.Water))), "Water"),
        button(cls := "min-h-14 rounded-2xl border border-amber/60 bg-surface text-lg uppercase text-amber",
          onClick --> (_.foreach(_ => app.go(Route.Food))), "Food"))
    )
