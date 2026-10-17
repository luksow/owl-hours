package owlhours.app.ui

import cats.data.EitherNel
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.concurrent.SignallingRef
import fs2.dom.HtmlElement
import owlhours.app.{AppState, Route}
import owlhours.app.logic.Amount
import owlhours.app.platform.Now
import owlhours.domain.*
import owlhours.app.logic.WhenPick.When

object FoodSheet {
  private val Presets = List("Snack" -> 250, "Light meal" -> 500, "Full meal" -> 900, "Feast" -> 1400)

  def apply(app: AppState): Resource[IO, HtmlElement[IO]] =
    (
      SignallingRef[IO].of("900"),
      SignallingRef[IO].of[When](When.Ago(0)),
      SignallingRef[IO].of(List.empty[String])
    ).tupled.toResource.flatMap { (kcal, when, errors) =>
      Sheet("Food", app)(
        Pickers.amountField("Energy (kcal)", kcal, Presets.map((n, k) => s"$n · $k kcal" -> k.toString)),
        Pickers.when(when, app.now),
        Sheet.errors(errors),
        Sheet.addButton {
          (kcal.get, when.get, Now.instant).flatMapN { (k, w, now) =>
            val meal: EitherNel[String, Intake] =
              (Amount.kcal(k).toEitherNel, Pickers.resolveWhen(w, now).toEitherNel).parMapN(Intake.Meal.apply)
            meal.fold(es => errors.set(es.toList), m => errors.set(Nil) *> app.addIntake(m) *> app.go(Route.Tonight))
          }
        }
      )
    }
}
