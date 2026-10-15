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
