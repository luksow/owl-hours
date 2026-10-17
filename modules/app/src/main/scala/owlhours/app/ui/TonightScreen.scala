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
import owlhours.app.logic.Neon.Tube
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
        val session = last.collect { case (s, closes) if s.endedAt.isEmpty && !now.isAfter(closes) => s }
        Key(profile, session, unit, if session.isEmpty then now.getEpochSecond / 3600 else 0L)
      }
      bundle <- shared(key) { k =>
        Bundle(k.session, k.unit, k.profile.map(p => Tonight.project(p, k.session, Instant.ofEpochSecond(k.hour * 3600 + 1800))))
      }
      view = (bundle, app.now).mapN((b, now) => b.projected.map(pr => (Tonight.at(pr, now), b.session, b.unit, now)))
      // Watt changes only with the state, so its animation is not restarted by the 30-second clock tick.
      watt = view.map(_.fold(Impairment.Sober)(_._1.impairment)).changes(using Eq.fromUniversalEquals).map(WattSvg.render(_, wall = false))
      screen <- div(
        cls := "mx-auto flex min-h-dvh max-w-md flex-col",
        headerTag(cls := "flex items-center gap-2 px-4 pt-3",
          div(cls := "w-32 shrink-0", InnerHtml(Signal.constant(BrandSvg.script))),
          span(cls := "whitespace-nowrap text-xs uppercase tracking-widest text-dim", app.now.map(t => s"Tonight · ${TimeFormat.weekday(t)}")),
          navTag(aria.label := "Sections", cls := "ml-auto flex gap-1",
            iconButton(app, Icon.Log, "Log", Route.Log),
            iconButton(app, Icon.History, "History", Route.History),
            iconButton(app, Icon.Settings, "Settings", Route.Settings))),
        // the sign: Watt on the brick wall, the number beside him (identity prototype, section 06)
        div(dataAttr("testid") := "sign", cls := "sign-wall mt-2 grid grid-cols-[9.5rem_1fr] items-center gap-1 px-3 py-2",
          div(dataAttr("testid") := "watt", cls := "w-[9.5rem]", InnerHtml(watt)),
          div(view.map {
            case None                    => div(())
            case Some((t, _, unit, now)) => reading(t, unit, now)
          })),
        div(view.map {
          case None => div(())
          case Some((t, s, unit, now)) => body(app, t, s, unit, now)
        }),
        p(cls := "mt-2 px-5 text-center text-xs uppercase tracking-wider text-dim", "Estimate only · never a green light to drive"),
        InstallCard.tonight(app),
        actionBar(app, bundle.map(_.session))
      )
    yield screen

  private def iconButton(app: AppState, icon: Icon, name: String, route: Route): Resource[IO, HtmlElement[IO]] =
    button(aria.label := name, cls := "grid size-12 place-items-center rounded-full border border-line bg-surface",
      onClick --> (_.foreach(_ => app.go(route))), InnerHtml(Signal.constant(Icons.svg(icon, Tube.White, 22))))

  /** The number, the trend and the state, beside Watt. */
  private def reading(t: Tonight, unit: BacUnit, now: Instant): Resource[IO, HtmlElement[IO]] =
    val current          = t.projection.curve.samples.findLast(!_.at.isAfter(now)).getOrElse(t.projection.curve.samples.head)
    val (number, suffix) = Display.bacParts(current.bacMgDl, current.bracMgL, unit)
    div(cls := "min-w-0",
      div(dataAttr("testid") := "bac", cls := "flex items-baseline font-ui font-bold leading-none tabular-nums",
        span(cls := "text-[4.4rem]", number), span(cls := "ml-1 text-2xl text-dim", suffix)),
      div(cls := "mt-1 font-ui text-sm uppercase tracking-widest text-amber",
        Display.trendLine(t.summary.trend, t.summary.peak, now, TimeFormat.hhmm)),
      div(dataAttr("testid") := "state", cls := "font-display text-4xl leading-tight text-pink neon", Display.impairment(t.impairment)))

  private def legend(swatch: String, text: String) =
    span(cls := "flex items-center gap-1", span(cls := swatch), text)

  private def body(app: AppState, t: Tonight, s: Option[Session], unit: BacUnit, now: Instant): Resource[IO, HtmlElement[IO]] =
    val chart = ChartSvg.render(t, now, Marker.of(s.toList.flatMap(_.intakes)), TimeFormat.utcOffsetMinutes, TimeFormat.hhmm,
      unit = unit)
    val peak = t.summary.peak.map(pk => s"${Display.peak(pk.bacMgDl, unit)} at ${TimeFormat.hhmm(pk.at)}")
    val back = Display.backTo(t, now, TimeFormat.hhmm, TimeFormat.weekday).map(_.stripPrefix("Back to ~0 "))
    def chip(dot: String, label: String, value: String) =
      div(cls := "flex items-center gap-2 rounded-xl border border-line bg-surface px-3 py-1.5",
        span(cls := s"oh-dot $dot"),
        div(span(cls := "block text-xs text-dim", label), span(cls := "block font-semibold tabular-nums", value)))
    div(
      div(dataAttr("testid") := "chart-card", cls := "mx-4 mt-2 rounded-2xl border border-line bg-surface p-2",
        div(cls := "flex items-center justify-between px-2 pt-1 font-ui",
          span(cls := "text-sm uppercase tracking-widest", "Your curve"),
          div(cls := "flex gap-3 text-[0.65rem] uppercase tracking-wider text-dim",
            legend("inline-block h-0.5 w-3.5 bg-amber", "so far"),
            legend("inline-block w-3.5 border-t-2 border-dashed border-amber", "projected"),
            legend("inline-block h-2 w-2.5 rounded-sm bg-amber/25", "varies"))),
        // InnerHtml replaces its element's children, so the chart gets its own element
        div(InnerHtml(Signal.constant(chart)))),
      Option.when(peak.isDefined || back.isDefined)(
        div(dataAttr("testid") := "chips", cls := "mx-4 mt-2 grid grid-cols-2 gap-2",
          peak.map(chip("amber", "Peak", _)),
          back.map(chip("ice", "Back to ~0", _)))),
      div(cls := "px-4", s.map(night => endNight(app, night, now))),
      Option.when(t.impairment == Impairment.TakeCare)(div(cls := "px-4", takeCare))
    )

  /** Offered only once the estimate is back to ~0 (Sessions.canEnd), so a new night never hides alcohol still in the blood. */
  private def endNight(app: AppState, night: Session, now: Instant): Resource[IO, HtmlElement[IO]] =
    div(app.persisted.map(_.profile.exists(Sessions.canEnd(_, night, now))).changes.map {
      case false => div(())
      case true =>
        div(cls := "mt-3 flex items-center justify-between gap-3 rounded-xl border border-line bg-surface px-3 py-2",
          span(cls := "text-dim", "Back to ~0."),
          button(cls := "min-h-12 rounded-xl px-3 font-semibold text-ice",
            onClick --> (_.foreach(_ => Now.instant.flatMap(app.endNight))), "End night"))
    })

  private def takeCare: Resource[IO, HtmlElement[IO]] =
    div(
      cls := "mt-4 rounded-2xl border border-care/60 bg-surface p-4 font-body",
      h2(cls := "font-ui text-2xl font-semibold text-care", "Let's slow everything down."),
      p(cls := "mt-2", "Your estimate is above 3‰, where alcohol poisoning is a real risk. Stay with people you trust and sip water."),
      p(cls := "mt-2", "If someone can't be woken, is breathing slowly or irregularly, is cold or pale, or is vomiting while drowsy, call emergency services now (112 in Europe, 911 in the US).")
    )

  private def actionBar(app: AppState, session: Signal[IO, Option[Session]]) =
    val lastDrink = session.map(_.flatMap(_.lastDrink))
    def icon(i: Icon, tube: Tube, px: Int) = span(cls := "inline-grid", InnerHtml(Signal.constant(Icons.svg(i, tube, px))))
    div(
      dataAttr("testid") := "action-bar",
      cls := "sticky bottom-0 mt-auto grid grid-cols-[1.5fr_1fr] grid-rows-[4rem_3.5rem] gap-2 bg-wall/95 px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]",
      button(cls := "flex items-center justify-center gap-2 rounded-2xl border border-pink bg-surface-2 text-2xl font-bold uppercase tracking-wider text-pink shadow-[0_0_18px_rgba(255,63,142,0.45)]",
        onClick --> (_.foreach(_ => app.go(Route.AddDrink))), icon(Icon.Drink, Tube.Pink, 26), "+ Drink"),
      button(cls := "flex items-center justify-center gap-2 rounded-2xl border border-line bg-surface text-base font-semibold uppercase disabled:opacity-40",
        disabled <-- lastDrink.map(_.isEmpty),
        onClick --> (_.foreach(_ =>
          (lastDrink.get, Now.instant).flatMapN {
            case (Some(d), now) => app.addIntake(Intake.Drink(d.snapshot, now, d.duration))
            case (None, _)      => IO.unit
          })),
        icon(Icon.Repeat, Tube.White, 20), "Same again"),
      button(cls := "flex items-center justify-center gap-2 rounded-2xl border border-ice/60 bg-surface text-lg font-semibold uppercase text-ice",
        onClick --> (_.foreach(_ => app.go(Route.Water))), icon(Icon.Water, Tube.Ice, 22), "Water"),
      button(cls := "flex items-center justify-center gap-2 rounded-2xl border border-amber/60 bg-surface text-lg font-semibold uppercase text-amber",
        onClick --> (_.foreach(_ => app.go(Route.Food))), icon(Icon.Food, Tube.Amber, 22), "Food")
    )
