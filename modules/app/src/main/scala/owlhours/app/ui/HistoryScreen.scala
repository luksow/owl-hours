package owlhours.app.ui

import calico.html.io.{*, given}
import cats.Eq
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.concurrent.Signal
import fs2.dom.HtmlElement
import owlhours.app.{AppState, Route}
import owlhours.app.logic.*
import owlhours.app.platform.{LocalDateTime, Now, TimeFormat}
import owlhours.domain.*
import owlhours.store.Persisted

object HistoryScreen:
  /** One simulation per night: recomputed when the saved data changes, never on the clock tick. */
  private def cards(app: AppState): Resource[IO, Signal[IO, Option[(Vector[NightSummary], BacUnit)]]] =
    app.persisted.discrete.changes(using Eq.fromUniversalEquals)
      .evalMap(p => Now.instant.map(now => Option(summaries(p, now))))
      .holdResource(Option.empty[(Vector[NightSummary], BacUnit)])

  private def summaries(p: Persisted, now: java.time.Instant): (Vector[NightSummary], BacUnit) =
    (p.profile.fold(Vector.empty)(prof => History.past(prof, p.sessions, now).map(History.summary(prof, _, p.settings.standardDrink))),
      p.settings.bacUnit)

  def apply(app: AppState): Resource[IO, HtmlElement[IO]] =
    cards(app).flatMap { cs =>
      div(
        cls := Screen.frame,
        Screen.header("History", app.go(Route.Tonight)),
        div(cs.map {
          case None                  => div(())
          case Some((Vector(), _))   => p(cls := "text-dim", "No past nights yet. A night moves here once it has closed.")
          case Some((nights, unit)) =>
            div(cls := "space-y-3", nights.toList.map { n =>
              button(
                cls := "w-full rounded-2xl border border-line bg-surface p-4 text-left",
                onClick --> (_.foreach(_ => app.go(Route.Night(n.session.id)))),
                div(cls := "font-ui text-lg uppercase tracking-widest text-amber", LocalDateTime.dayLabel(n.session.startedAt)),
                div(cls := "mt-1 font-ui text-lg tabular-nums", Display.night(n, unit)),
                Option.when(n.badges.nonEmpty)(div(cls := "mt-2 flex flex-wrap gap-2",
                  n.badges.map(b => span(cls := "rounded-full border border-ice/60 px-3 py-1 font-ui text-sm text-ice", b.title))))
              )
            })
        })
      )
    }

  def night(app: AppState, id: SessionId): Resource[IO, HtmlElement[IO]] =
    div(
      cls := Screen.frame,
      Screen.header("Night", app.go(Route.History), "Back"),
      div(app.persisted.map(p => (p.profile, p.sessions.find(_.id == id), p.settings)).changes(using Eq.fromUniversalEquals).map {
        case (Some(profile), Some(s), settings) => detail(profile, s, settings)
        case _ => p(cls := "text-dim", "That night isn't in your history any more. It may have been edited or deleted.")
      })
    )

  private def detail(profile: Profile, s: Session, settings: Settings): Resource[IO, HtmlElement[IO]] =
    val n        = History.summary(profile, s, settings.standardDrink)
    val (t, end) = History.chart(profile, s)
    val day      = LocalDateTime.dayLabel(s.startedAt)
    val chart = ChartSvg.render(t, end, Marker.of(s.intakes), TimeFormat.utcOffsetMinutes, TimeFormat.hhmm,
      bacLabel = p => Display.gridLabel(p, settings.bacUnit), label = s"Blood alcohol estimate on $day", showNow = false)
    div(
      cls := "space-y-4",
      h2(cls := "font-ui text-lg uppercase tracking-widest text-amber", day),
      p(cls := "font-ui text-lg tabular-nums", Display.night(n, settings.bacUnit)),
      p(cls := "text-sm text-dim", s"Standard drink: ${Display.standardDrinkName(settings.standardDrink)}"),
      div(cls := "rounded-2xl border border-line bg-surface p-2", InnerHtml(Signal.constant(chart))),
      Option.when(n.badges.nonEmpty)(div(cls := "space-y-3", n.badges.map(b =>
        div(cls := "flex items-center gap-4 rounded-2xl border border-ice/60 bg-surface p-4",
          BrandSvg.badge(b).map(art => div(dataAttr("testid") := "badge-art", cls := "size-20 shrink-0", InnerHtml(Signal.constant(art)))),
          div(
            h3(cls := "font-display text-3xl text-ice", b.title),
            p(cls := "mt-1 text-dim", b.why)))))),
      ul(cls := "space-y-2", s.intakes.toList.map(i =>
        li(cls := "rounded-xl border border-line bg-surface px-3 py-2", s"${TimeFormat.hhmm(i.startsAt)} · ${Display.intake(i)}"))),
      p(cls := "text-sm text-dim", "To change an entry, open the Log.")
    )
