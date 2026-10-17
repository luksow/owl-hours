package owlhours.domain

import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset}
import scala.util.Try

/**
 * ISO-8601 text for instants and dates without java.time's DateTimeFormatter. On Scala.js the formatter and its
 * locale data are a large share of the bundle, and one `Instant.parse` keeps them all (see learning note 06).
 */
object IsoTime:
  private val InstantShape = """(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?Z""".r
  private val DateShape    = """(\d{4})-(\d{2})-(\d{2})""".r
  private val LocalShape   = """(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})""".r

  def formatDate(d: LocalDate): String = f"${d.getYear}%04d-${d.getMonthValue}%02d-${d.getDayOfMonth}%02d"

  def parseDate(s: String): Option[LocalDate] = s match
    case DateShape(y, m, d) => Try(LocalDate.of(y.toInt, m.toInt, d.toInt)).toOption
    case _                  => None

  /** The same text as `Instant.toString`: seconds always, the fraction in groups of three digits when not zero. */
  def formatInstant(t: Instant): String =
    val dt   = LocalDateTime.ofEpochSecond(t.getEpochSecond, t.getNano, ZoneOffset.UTC)
    val nano = t.getNano
    val fraction =
      if nano == 0 then ""
      else if nano % 1000000 == 0 then f".${nano / 1000000}%03d"
      else if nano % 1000 == 0 then f".${nano / 1000}%06d"
      else f".$nano%09d"
    s"${formatDate(dt.toLocalDate)}T${f"${dt.getHour}%02d:${dt.getMinute}%02d:${dt.getSecond}%02d"}${fraction}Z"

  def parseInstant(s: String): Option[Instant] = s match
    case InstantShape(y, mo, d, h, mi, sec, frac) =>
      Try {
        val date = LocalDate.of(y.toInt, mo.toInt, d.toInt)
        val (hh, mm, ss) = (h.toInt, mi.toInt, Option(sec).fold(0)(_.toInt))
        require(hh < 24 && mm < 60 && ss < 60)
        val nanos = Option(frac).fold(0)(f => (f + "00000000").take(9).toInt)
        Instant.ofEpochSecond(date.toEpochDay * 86400L + hh * 3600L + mm * 60L + ss, nanos.toLong)
      }.toOption
    case _ => None

  def isLocalDateTime(s: String): Boolean = s match
    case LocalShape(y, mo, d, h, mi) =>
      Try(LocalDate.of(y.toInt, mo.toInt, d.toInt)).isSuccess && h.toInt < 24 && mi.toInt < 60
    case _ => false
