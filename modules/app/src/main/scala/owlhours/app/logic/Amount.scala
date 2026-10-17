package owlhours.app.logic

import owlhours.domain.*

/** Parses numbers typed by people ("0,5", " 500 ") into validated domain quantities. */
object Amount {
  def ml(text: String): Either[String, Millilitres] = number("Amount", text).flatMap(Millilitres.from)
  /** Drinks and water: below 5 ml (less than a dash) is almost surely litres typed into a ml field, e.g. "0,5". */
  def drinkMl(text: String): Either[String, Millilitres] = ml(text).flatMap { v =>
    if v.unwrap < 5 then Left(s"Amounts are in ml — did you mean ${Format.number(v.unwrap * 1000)} ml?") else Right(v)
  }

  /** One line of a cocktail: 0 leaves it out. */
  def ingredientMl(text: String): Either[String, Double] = number("Amount", text).flatMap { v =>
    if v == 0 then Right(0.0) else Millilitres.from(v).map(_.unwrap)
  }

  def minutes(text: String): Either[String, Minutes] = number("Duration", text).flatMap(Minutes.from)
  def kcal(text: String): Either[String, Kcal]       = number("Energy", text).flatMap(Kcal.from)
  def abv(text: String): Either[String, Abv]         = number("ABV", text).flatMap(Abv.from)

  private def number(label: String, s: String): Either[String, Double] =
    s.trim.replace(',', '.').toDoubleOption.filter(_.isFinite).toRight(s"$label must be a number")
}
