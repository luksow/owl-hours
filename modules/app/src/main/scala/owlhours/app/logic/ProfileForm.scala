package owlhours.app.logic

import cats.data.EitherNel
import cats.syntax.all.*
import owlhours.domain.*

object ProfileForm:
  final case class Fields(sex: String, age: String, weightKg: String, heightCm: String)

  def validate(f: Fields): EitherNel[String, Profile] =
    (sex(f.sex), number("Age", f.age).flatMap(x => Age.from(x.toInt)).toEitherNel,
      number("Weight", f.weightKg).flatMap(BodyWeight.from).toEitherNel,
      number("Height", f.heightCm).flatMap(Height.from).toEitherNel).parMapN(Profile.apply)

  private def sex(s: String): EitherNel[String, Sex] = s.trim.toLowerCase match
    case "male"   => Right(Sex.Male)
    case "female" => Right(Sex.Female)
    case _        => Left("Choose the sex the model should use").toEitherNel

  /** Accepts "72,5" as well as "72.5"; age is rounded down to whole years by the caller. */
  private def number(label: String, s: String): Either[String, Double] =
    s.trim.replace(',', '.').toDoubleOption.filter(_.isFinite).toRight(s"$label must be a number")
