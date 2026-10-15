package owlhours.app.platform

import java.time.Instant
import scala.scalajs.js

/** The browser's local wall-clock time, as used by <input type="datetime-local">. DST-correct via JS Date. */
object LocalDateTime:
  private val Shape = """\d{4}-\d{2}-\d{2}T\d{2}:\d{2}""".r

  def format(t: Instant): String =
    val d = new js.Date(t.toEpochMilli.toDouble)
    def two(n: Double) = f"${n.toInt}%02d"
    s"${d.getFullYear().toInt}-${two(d.getMonth() + 1)}-${two(d.getDate())}T${two(d.getHours())}:${two(d.getMinutes())}"

  /** ECMAScript reads a date-time without a zone suffix as local time. */
  def parse(s: String): Option[Instant] = parseDetailed(s).toOption

  def parseDetailed(s: String): Either[String, Instant] =
    val text = s.trim
    if !Shape.matches(text) then Left("Pick a date and time")
    else
      val ms = new js.Date(text).getTime()
      if !ms.isNaN && format(Instant.ofEpochMilli(ms.toLong)) == text then Right(Instant.ofEpochMilli(ms.toLong))
      // a real calendar date-time that the browser cannot represent locally is the hour skipped when clocks go forward
      else if scala.util.Try(java.time.LocalDateTime.parse(text)).isSuccess then
        Left("That time doesn't exist tonight: the clocks went forward")
      else Left("Pick a date and time")

  def dayLabel(t: Instant): String =
    new js.Date(t.toEpochMilli.toDouble)
      .asInstanceOf[js.Dynamic]
      .toLocaleDateString(js.undefined, js.Dynamic.literal(weekday = "short", day = "numeric", month = "short"))
      .asInstanceOf[String]
