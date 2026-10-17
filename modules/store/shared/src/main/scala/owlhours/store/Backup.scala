package owlhours.store

import io.circe.{Json, parser}
import io.circe.syntax.*
import java.time.Instant
import owlhours.domain.IsoTime

/** The export file: everything on the device, versioned so a newer file is refused instead of misread. */
object Backup {
  val Format  = "owl-hours-backup"
  val Version = 1

  val NotABackup = "This file isn't an Owl Hours backup, so nothing was changed."
  private val Newer = "This backup comes from a newer version of Owl Hours, so nothing was changed. Update the app, then import it again."
  private def damaged(part: String) = s"This backup is damaged (its $part could not be read), so nothing was changed."

  def encode(p: Persisted, exportedAt: Instant): String =
    Json.obj(
      "format"     -> Format.asJson,
      "version"    -> Version.asJson,
      "exportedAt" -> IsoTime.formatInstant(exportedAt).asJson,
      "profile"    -> p.profile.fold(Json.Null)(Codecs.profileCodec(_)),
      "settings"   -> Codecs.settingsCodec(p.settings),
      "sessions"   -> Codecs.sessionsCodec(p.sessions)
    ).spaces2

  def decode(text: String): Either[String, Persisted] =
    for {
      json     <- parser.parse(text).left.map(_ => NotABackup)
      c         = json.hcursor
      _        <- c.get[String]("format").toOption.filter(_ == Format).toRight(NotABackup)
      version  <- c.get[Int]("version").left.map(_ => NotABackup)
      _        <- Either.cond(version <= Version, (), Newer)
      profile  <- c.downField("profile").as(using Codecs.profileCodec).left.map(_ => damaged("profile"))
      settings <- c.downField("settings").as(using Codecs.settingsCodec).left.map(_ => damaged("settings"))
      sessions <- c.downField("sessions").as(using Codecs.sessionsCodec).left.map(_ => damaged("nights"))
    }
    yield Persisted(disclaimerAccepted = true, Some(profile), settings, sessions.filter(_.intakes.nonEmpty))
}
