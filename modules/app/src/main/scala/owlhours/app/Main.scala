package owlhours.app

import calico.*
import calico.html.io.{*, given}
import cats.effect.*
import cats.syntax.all.*
import fs2.concurrent.Signal
import fs2.dom.HtmlElement
import owlhours.app.ui.{AddDrinkSheet, FoodSheet, LogScreen, Onboarding, TonightScreen, UpdateToast, WaterSheet}

object Main extends IOWebApp:
  def render: Resource[IO, HtmlElement[IO]] =
    AppState.resource(window).flatMap { app =>
      val screen: Signal[IO, Resource[IO, HtmlElement[IO]]] =
        (app.catalogue, app.persisted.map(p => p.disclaimerAccepted && p.profile.isDefined).changes, app.route).mapN {
          case (CatalogueState.Loading, _, _)        => splash
          case (CatalogueState.Failed(errors), _, _) => catalogueFailed(errors)
          case (CatalogueState.Ready(_), false, _)   => Onboarding(app)
          case (CatalogueState.Ready(c), true, Route.Tonight)  => TonightScreen(app, c)
          case (CatalogueState.Ready(c), true, Route.AddDrink) => AddDrinkSheet(app, c)
          case (CatalogueState.Ready(_), true, Route.Water)    => WaterSheet(app)
          case (CatalogueState.Ready(_), true, Route.Food)     => FoodSheet(app)
          case (CatalogueState.Ready(_), true, Route.Log)      => LogScreen(app)
        }
      div(
        cls := "min-h-dvh bg-wall text-ink font-ui",
        problemsBanner(app),
        div(screen),
        UpdateToast()
      )
    }

  private def splash: Resource[IO, HtmlElement[IO]] =
    div(cls := "grid min-h-dvh place-items-center", h1(cls := "font-display text-6xl text-pink neon", "Owl Hours"))

  private def catalogueFailed(errors: List[String]): Resource[IO, HtmlElement[IO]] =
    div(
      cls := "mx-auto max-w-xl p-6 font-body",
      h1(cls := "font-ui text-2xl font-semibold text-warn", "The drink list could not be loaded"),
      p(cls := "mt-2 text-dim", "Try reloading. If this keeps happening, these details help us fix it:"),
      ul(cls := "mt-4 list-disc pl-5 text-sm", errors.map(e => li(e)))
    )

  private def problemsBanner(app: AppState): Resource[IO, HtmlElement[IO]] =
    div(app.problems.map {
      case Nil => div(())
      case ps =>
        div(
          cls := "m-3 rounded-2xl border border-warn/60 bg-surface p-4 font-body text-sm",
          ps.map(p(_)),
          button(cls := "mt-2 min-h-12 rounded-xl px-4 text-ice", onClick --> (_.foreach(_ => app.dismissProblems)), "OK")
        )
    })
