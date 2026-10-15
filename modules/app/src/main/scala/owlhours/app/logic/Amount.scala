package owlhours.app.logic

import owlhours.domain.*

/** Parses numbers typed by people ("0,5", " 500 ") into validated domain quantities. */
object Amount:
  def ml(text: String): Either[String, Millilitres] = number("Amount", text).flatMap(Millilitres.from)
  def minutes(text: String): Either[String, Minutes] = number("Duration", text).flatMap(Minutes.from)
  def kcal(text: String): Either[String, Kcal]       = number("Energy", text).flatMap(Kcal.from)
  def abv(text: String): Either[String, Abv]         = number("ABV", text).flatMap(Abv.from)

  private def number(label: String, s: String): Either[String, Double] =
    s.trim.replace(',', '.').toDoubleOption.filter(_.isFinite).toRight(s"$label must be a number")
