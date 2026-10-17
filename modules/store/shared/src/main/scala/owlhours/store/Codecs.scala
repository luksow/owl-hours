package owlhours.store

import io.circe.*
import io.circe.syntax.*
import java.time.{Instant, LocalDate}
import owlhours.domain.*
import pl.iterators.kebs.circe.*

/** JSON for persisted state. Case classes are derived by kebs-circe; opaque types go through their validators. */
object Codecs {
  private def byName[E](values: Array[E], what: String): Decoder[E] =
    Decoder.decodeString.emap(s => values.find(_.toString == s).toRight(s"unknown $what '$s'"))

  given Encoder[Sex]     = Encoder.encodeString.contramap(_.toString)
  given Decoder[Sex]     = byName(Sex.values, "sex")
  given Encoder[BacUnit] = Encoder.encodeString.contramap(_.toString)
  given Decoder[BacUnit] = byName(BacUnit.values, "BAC unit")
  given Encoder[StandardDrink] = Encoder.encodeString.contramap(_.toString)
  given Decoder[StandardDrink] = byName(StandardDrink.values, "standard drink")

  // hand-written ISO text keeps java.time's formatter out of the Scala.js bundle (IsoTime)
  given Encoder[Instant]   = Encoder.encodeString.contramap(IsoTime.formatInstant)
  given Decoder[Instant]   = Decoder.decodeString.emap(s => IsoTime.parseInstant(s).toRight(s"'$s' is not a time"))
  given Encoder[LocalDate] = Encoder.encodeString.contramap(IsoTime.formatDate)
  given Decoder[LocalDate] = Decoder.decodeString.emap(s => IsoTime.parseDate(s).toRight(s"'$s' is not a date"))

  given Encoder[Intake] = Encoder.instance {
    case Intake.Drink(snapshot, start, duration) =>
      Json.obj("type" -> "drink".asJson, "snapshot" -> snapshot.asJson, "start" -> start.asJson, "duration" -> duration.asJson)
    case Intake.Water(volume, start, duration) =>
      Json.obj("type" -> "water".asJson, "volume" -> volume.asJson, "start" -> start.asJson, "duration" -> duration.asJson)
    case Intake.Meal(kcal, at) =>
      Json.obj("type" -> "meal".asJson, "kcal" -> kcal.asJson, "at" -> at.asJson)
  }

  given Decoder[Intake] = Decoder.instance { c =>
    c.downField("type").as[String].flatMap {
      case "drink" =>
        for {
          s <- c.downField("snapshot").as[DrinkSnapshot]
          t <- c.downField("start").as[Instant]
          d <- c.downField("duration").as[Minutes]
        }
        yield Intake.Drink(s, t, d)
      case "water" =>
        for {
          v <- c.downField("volume").as[Millilitres]
          t <- c.downField("start").as[Instant]
          d <- c.downField("duration").as[Minutes]
        }
        yield Intake.Water(v, t, d)
      case "meal" =>
        for {
          k <- c.downField("kcal").as[Kcal]
          t <- c.downField("at").as[Instant]
        }
        yield Intake.Meal(k, t)
      case other => Left(DecodingFailure(s"unknown intake type '$other'", c.history))
    }
  }

  val profileCodec: Codec[Profile]           = Codec.from(summon[Decoder[Profile]], summon[Encoder[Profile]])
  val settingsCodec: Codec[Settings] = Codec.from(
    Decoder.instance(c =>
      for {
        unit     <- c.downField("bacUnit").as[BacUnit]
        standard <- c.downField("standardDrink").as[Option[StandardDrink]]
      }
      yield Settings(unit, standard.getOrElse(StandardDrink.default))),
    Encoder.instance(s => Json.obj("bacUnit" -> s.bacUnit.asJson, "standardDrink" -> s.standardDrink.asJson))
  )
  val sessionsCodec: Codec[Vector[Session]]  = Codec.from(summon[Decoder[Vector[Session]]], summon[Encoder[Vector[Session]]])
}
