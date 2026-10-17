package owlhours.app.platform

import java.time.Instant
import scala.scalajs.js

/** Uses the browser's locale and time zone (java.time on Scala.js has no time-zone database). */
object TimeFormat:
  def hhmm(t: Instant): String =
    new js.Date(t.toEpochMilli.toDouble)
      .asInstanceOf[js.Dynamic]
      .toLocaleTimeString(js.undefined, js.Dynamic.literal(hour = "2-digit", minute = "2-digit"))
      .asInstanceOf[String]

  /** Local offset from UTC at `at`, in minutes (JS reports it with the opposite sign). */
  /** The local short weekday ("Sat"), for Tonight's header. */
  def weekday(t: Instant): String =
    new js.Date(t.toEpochMilli.toDouble)
      .asInstanceOf[js.Dynamic]
      .toLocaleDateString(js.undefined, js.Dynamic.literal(weekday = "short"))
      .asInstanceOf[String]

  def utcOffsetMinutes(at: Instant): Int = -new js.Date(at.toEpochMilli.toDouble).getTimezoneOffset().toInt
