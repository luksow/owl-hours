package owlhours.app.logic

import cats.data.EitherNel
import cats.syntax.all.*
import java.time.{LocalDate, Period}
import owlhours.domain.*

object ProfileForm {
  final case class Fields(sex: String, born: String, weightKg: String, heightCm: String)

  object Fields {
    def of(p: Profile): Fields =
      Fields(
        p.sex.toString.toLowerCase,
        IsoTime.formatDate(p.born),
        Format.number(p.weight.unwrap),
        Format.number(p.height.unwrap)
      )
  }

  def validate(f: Fields, today: LocalDate): EitherNel[String, Profile] =
    (
      sex(f.sex),
      born(f.born, today).toEitherNel,
      number("Weight", f.weightKg).flatMap(BodyWeight.from).toEitherNel,
      number("Height", f.heightCm).flatMap(Height.from).toEitherNel
    ).parMapN(Profile.apply)

  private def sex(s: String): EitherNel[String, Sex] = s.trim.toLowerCase match {
    case "male"   => Right(Sex.Male)
    case "female" => Right(Sex.Female)
    case _        => Left("Choose the sex the model should use").toEitherNel
  }

  private def born(s: String, today: LocalDate): Either[String, LocalDate] =
    IsoTime.parseDate(s.trim).toRight("Enter your date of birth").flatMap { d =>
      val years = Period.between(d, today).getYears
      if years < 18 then Left("Owl Hours is for adults (18+)") else Age.from(years).map(_ => d)
    }

  /** Accepts "72,5" as well as "72.5". */
  private def number(label: String, s: String): Either[String, Double] =
    s.trim.replace(',', '.').toDoubleOption.filter(_.isFinite).toRight(s"$label must be a number")
}
