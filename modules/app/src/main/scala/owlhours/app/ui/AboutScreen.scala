package owlhours.app.ui

import calico.html.io.{*, given}
import cats.effect.{IO, Resource}
import fs2.concurrent.Signal
import fs2.dom.HtmlElement
import owlhours.app.{AppState, Route}
import owlhours.app.logic.BrandSvg

object AboutScreen:
  private def para(text: String) = p(cls := "font-body text-ink/90", text)
  private def cite(text: String) = li(cls := "font-body text-sm text-dim", text)

  def apply(app: AppState): Resource[IO, HtmlElement[IO]] =
    div(
      cls := Screen.frame,
      Screen.header("About the science", app.go(Route.Tonight)),
      div(cls := "mx-auto mb-6 w-full max-w-xs", InnerHtml(Signal.constant(BrandSvg.wordmark(lit = true)))),
      Screen.section("How the estimate works")(
        para("Owl Hours runs a published physiological model of how a body handles alcohol: how quickly it leaves the stomach " +
          "(more slowly after food, and with drinks that carry other energy, such as sugar), how it is absorbed, how it spreads " +
          "through blood and body water, and how the liver breaks it down."),
        para("The model is sized to you from your sex, age, weight and height. It was built and fitted to drinking studies by " +
          "Podéus, Cedersund and colleagues at Linköping University. Owl Hours is an independent port that reproduces the " +
          "authors' published curves.")),
      Screen.section("The shaded band")(
        para("The band around the curve is typical person-to-person variation: the same model run for people who absorb and " +
          "clear alcohol faster or more slowly than average. Its upper edge is never below a classic Widmark estimate. It is " +
          "not a statistical guarantee, and your real value can fall outside it.")),
      Screen.section("What it can't know")(
        para("It is an estimate from what you log, not a measurement. It doesn't know about medicines, illness, liver " +
          "conditions, pregnancy, tiredness, or drinks you didn't log. Drink strengths in the list are typical values, and your " +
          "drink may differ. Breath values are converted from the blood estimate.")),
      Screen.section("Never for driving")(
        para("Never use Owl Hours to decide whether you can drive, cycle, operate machinery or look after others. " +
          "No number on this app means it is safe to drive."),
        para("Only for adults of legal drinking age where you are. If someone may have alcohol poisoning (they can't be woken, " +
          "breathe slowly or irregularly, are cold or pale, or vomit while drowsy), call emergency services: 112 in Europe, 911 in the US.")),
      Screen.section("Privacy")(
        para("No accounts, no analytics, no cookies, no ads. Everything you enter stays in this browser on this device, and " +
          "Owl Hours never sends it anywhere. Export a backup from Settings if you want a copy.")),
      Screen.section("Credits")(
        ul(cls := "space-y-2",
          cite("Podéus H. et al. A physiologically-based digital twin for alcohol consumption — predicting real-life drinking responses " +
            "and long-term plasma PEth. npj Digital Medicine 7, 112 (2024). doi:10.1038/s41746-024-01089-6"),
          cite("Podéus H. et al. A digital twin framework for forensic reconstruction of alcohol intake via fast and slow metabolite " +
            "kinetics. Scientific Reports 16, 9336 (2026). doi:10.1038/s41598-026-44093-4"),
          cite("Model code: github.com/willov/alcohol_app, MIT licence © 2024 Lövfors, Simonsson, Podéus."),
          cite("Owl Hours is open source under the Apache 2.0 licence. Fonts: Barlow, Barlow Condensed and Mr Dafoe, under the SIL Open Font Licence."),
          cite("Brand names in the drink list are used only to describe drinks."))),
      Screen.makersPlate
    )
