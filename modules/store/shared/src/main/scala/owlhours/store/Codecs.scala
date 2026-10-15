package owlhours.store

import io.circe.*
import io.circe.syntax.*
import java.time.Instant
import owlhours.domain.*
import pl.iterators.kebs.circe.*

/** JSON for persisted state. Case classes are derived by kebs-circe; opaque types go through their validators. */
object Codecs:
  private def byName[E](values: Array[E], what: String): Decoder[E] =
    Decoder.decodeString.emap(s => values.find(_.toString == s).toRight(s"unknown $what '$s'"))

  given Encoder[Sex]     = Encoder.encodeString.contramap(_.toString)
  given Decoder[Sex]     = byName(Sex.values, "sex")
  given Encoder[BacUnit] = Encoder.encodeString.contramap(_.toString)
  given Decoder[BacUnit] = byName(BacUnit.values, "BAC unit")

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
        for
          s <- c.downField("snapshot").as[DrinkSnapshot]
          t <- c.downField("start").as[Instant]
          d <- c.downField("duration").as[Minutes]
        yield Intake.Drink(s, t, d)
      case "water" =>
        for
          v <- c.downField("volume").as[Millilitres]
          t <- c.downField("start").as[Instant]
          d <- c.downField("duration").as[Minutes]
        yield Intake.Water(v, t, d)
      case "meal" =>
        for
          k <- c.downField("kcal").as[Kcal]
          t <- c.downField("at").as[Instant]
        yield Intake.Meal(k, t)
      case other => Left(DecodingFailure(s"unknown intake type '$other'", c.history))
    }
  }

  val profileCodec: Codec[Profile]           = Codec.from(summon[Decoder[Profile]], summon[Encoder[Profile]])
  val settingsCodec: Codec[Settings]         = Codec.from(summon[Decoder[Settings]], summon[Encoder[Settings]])
  val sessionsCodec: Codec[Vector[Session]]  = Codec.from(summon[Decoder[Vector[Session]]], summon[Encoder[Vector[Session]]])
