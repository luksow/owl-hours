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

object WaterSheet:
  def apply(app: AppState): Resource[IO, HtmlElement[IO]] =
    (SignallingRef[IO].of("250"), SignallingRef[IO].of("5"), SignallingRef[IO].of[When](When.Ago(0)),
      SignallingRef[IO].of(List.empty[String])).tupled.toResource.flatMap { (amount, duration, when, errors) =>
      Sheet("Water", app)(
        Pickers.amountField("Amount (ml)", amount, List("250", "330", "500").map(v => s"$v ml" -> v)),
        Pickers.when(when, app.now),
        Pickers.minutesField(duration),
        Sheet.errors(errors),
        Sheet.addButton {
          (amount.get, duration.get, when.get, Now.instant).flatMapN { (a, d, w, now) =>
            val intake: EitherNel[String, Intake] =
              (Amount.drinkMl(a).toEitherNel, Pickers.resolveWhen(w, now).toEitherNel, Amount.minutes(d).toEitherNel)
                .parMapN(Intake.Water.apply)
            intake.fold(es => errors.set(es.toList), i => errors.set(Nil) *> app.addIntake(i) *> app.go(Route.Tonight))
          }
        }
      )
    }
