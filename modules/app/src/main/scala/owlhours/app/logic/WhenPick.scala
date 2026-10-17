package owlhours.app.logic

import java.time.Instant

/** The "When" field (spec §6): shortcut chips and a date-time field that always shows the chosen time. */
object WhenPick {
  enum When {
    case Ago(minutes: Int)
    case At(local: String)
  }

  val Agos: List[(Int, String)] = List(0 -> "Now", 15 -> "15 min ago", 30 -> "30 min ago", 60 -> "60 min ago")

  def text(w: When, now: Instant, format: Instant => String): String = w match {
    case When.Ago(m) => format(now.minusSeconds(m * 60L))
    case When.At(s)  => s
  }

  def typed(text: String, now: Instant, format: Instant => String): When =
    Agos
      .collectFirst { case (m, _) if format(now.minusSeconds(m * 60L)) == text => When.Ago(m) }
      .getOrElse(When.At(text))

  def resolve(w: When, now: Instant, parse: String => Either[String, Instant]): Either[String, Instant] = w match {
    case When.Ago(m) => Right(now.minusSeconds(m * 60L))
    case When.At(s)  => parse(s).flatMap(Edits.notInFuture(_, now))
  }
}
