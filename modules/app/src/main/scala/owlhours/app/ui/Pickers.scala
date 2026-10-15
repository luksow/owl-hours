package owlhours.app.ui

import calico.html.io.{*, given}
import cats.Eq
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.concurrent.{Signal, SignallingRef}
import fs2.dom.HtmlElement
import java.time.Instant
import owlhours.app.logic.Edits
import owlhours.app.platform.{LocalDateTime, Now}

/** Shared controls for the logging sheets: chips that write into the same state a text field edits. */
object Pickers:
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

  enum When:
    case Ago(minutes: Int)
    case At(local: String)

  def resolveWhen(w: When, now: Instant): Either[String, Instant] = w match
    case When.Ago(m) => Right(now.minusSeconds(m * 60L))
    case When.At(s)  => LocalDateTime.parseDetailed(s).flatMap(Edits.notInFuture(_, now))

  private val Ago = List(0 -> "Now", 15 -> "15 min ago", 30 -> "30 min ago", 60 -> "60 min ago")

  def when(state: SignallingRef[IO, When]): Resource[IO, HtmlElement[IO]] =
    // The date-time input is built once when "Pick time" is chosen, so typing into it never rebuilds it.
    val picking = state.map { case When.At(_) => true; case _ => false }.changes(using Eq.fromUniversalEquals)
    div(
      span(cls := caption, "When"),
      div(cls := "mt-1 flex flex-wrap gap-2",
        Ago.map((m, t) => chip(t, state.map(_ == When.Ago(m)), state.set(When.Ago(m)))),
        chip("Pick time", picking, // idempotent: tapping it again must not replace a time already picked
          Now.instant.flatMap(now => state.update {
            case picked: When.At => picked
            case _               => When.At(LocalDateTime.format(now))
          }))),
      div(picking.map {
        case false => div(())
        case true =>
          (state.get, Now.instant).tupled.toResource.flatMap { (current, now) =>
            val initial = current match
              case When.At(s) => s
              case _          => LocalDateTime.format(now)
            label(cls := "mt-2 block",
              span(cls := caption, "Started at"),
              input.withSelf { self =>
                (typ := "datetime-local", cls := field, value := initial, maxAttr := LocalDateTime.format(now),
                  onInput --> (_.foreach(_ => self.value.get.flatMap(v => state.set(When.At(v))))))
              })
          }
      })
    )

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
