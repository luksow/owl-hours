package owlhours.app.ui

import calico.html.io.{*, given}
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.concurrent.{Signal, SignallingRef}
import fs2.dom.HtmlElement
import java.time.Instant
import owlhours.app.logic.WhenPick
import owlhours.app.logic.WhenPick.When
import owlhours.app.platform.{LocalDateTime, Now}

/** Shared controls for the logging sheets: chips that write into the same state a text field edits. */
object Pickers {
  private val chipOn  = List("min-h-12", "rounded-xl", "border", "border-pink", "bg-surface-2", "px-3", "text-pink")
  private val chipOff = List("min-h-12", "rounded-xl", "border", "border-line", "bg-surface", "px-3")
  private val field   = "mt-1 min-h-12 w-full rounded-xl border border-line bg-surface px-3 text-lg text-ink"
  private val caption = "block font-ui text-sm uppercase tracking-wider text-dim"

  def chip(text: String, on: Signal[IO, Boolean], action: IO[Unit]): Resource[IO, HtmlElement[IO]] =
    button(
      cls <-- on.map(if _ then chipOn else chipOff),
      aria.pressed <-- on.map(_.toString),
      onClick --> (_.foreach(_ => action)),
      text
    )

  def resolveWhen(w: When, now: Instant): Either[String, Instant] = WhenPick.resolve(w, now, LocalDateTime.parseDetailed)

  /** Spec §6: shortcut chips fill a date-time field that is always shown; typing a chip's time selects that chip. */
  def when(state: SignallingRef[IO, When], now: Signal[IO, Instant]): Resource[IO, HtmlElement[IO]] = {
    val shown = (state, now).mapN((w, t) => WhenPick.text(w, t, LocalDateTime.format))
    div(
      span(cls := caption, "When"),
      div(cls := "mt-1 flex flex-wrap gap-2",
        WhenPick.Agos.map((m, t) => chip(t, state.map(_ == When.Ago(m)), state.set(When.Ago(m))))),
      label(cls := "mt-2 block",
        span(cls := caption, "Started at"),
        input.withSelf { self =>
          (typ := "datetime-local", cls := field, maxAttr <-- now.map(LocalDateTime.format),
            onInput --> (_.foreach(_ => (self.value.get, Now.instant).flatMapN((v, t) =>
              state.set(WhenPick.typed(v, t, LocalDateTime.format))))))
        }.flatTap { el =>
          // written only when it differs: echoing a half-typed (empty) value back makes Chromium clear every segment
          shown.discrete.foreach(v => el.value.get.flatMap(cur => IO.whenA(cur != v)(el.value.set(v)))).compile.drain.background
        })
    )
  }

  def amountField(caption0: String, text: SignallingRef[IO, String], presets: List[(String, String)]): Resource[IO, HtmlElement[IO]] =
    div(
      div(cls := "flex flex-wrap gap-2", presets.map((t, v) => chip(t, text.map(_ == v), text.set(v)))),
      label(cls := "mt-2 block",
        span(cls := caption, caption0),
        input.withSelf { self =>
          (typ := "text", inputMode := "decimal", cls := field, value <-- text,
            onInput --> (_.foreach(_ => self.value.get.flatMap(text.set))))
        })
    )

  def minutesField(text: SignallingRef[IO, String]): Resource[IO, HtmlElement[IO]] =
    amountField("Drinking time (min)", text, List("1", "5", "15", "30", "60").map(m => s"$m min" -> m))
}
