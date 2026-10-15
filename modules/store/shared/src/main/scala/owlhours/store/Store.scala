package owlhours.store

import cats.effect.kernel.Sync
import cats.syntax.all.*
import io.circe.{Decoder, DecodingFailure, parser}
import io.circe.syntax.*
import java.time.Instant
import owlhours.domain.*

/** Minimal storage interface; the browser implementation wraps localStorage. */
trait KeyValue[F[_]]:
  def get(key: String): F[Option[String]]
  def set(key: String, value: String): F[Unit]
  def remove(key: String): F[Unit]

  /** False when storage is blocked (private mode, disabled site data); nothing will survive a reload. */
  def available: F[Boolean]

final case class Persisted(disclaimerAccepted: Boolean, profile: Option[Profile], settings: Settings, sessions: Vector[Session])

object Persisted:
  val empty: Persisted = Persisted(disclaimerAccepted = false, None, Settings.default, Vector.empty)

final case class Loaded(persisted: Persisted, problems: List[String])

final class Store[F[_]](kv: KeyValue[F])(using F: Sync[F]):
  import Store.Keys

  def load: F[Loaded] =
    for
      usable     <- kv.available
      now        <- F.realTime.map(d => Instant.ofEpochMilli(d.toMillis)) // realTimeInstant is JVM-only
      disclaimer <- read(Keys.disclaimer, "your disclaimer choice", now)(using Decoder.decodeBoolean)
      profile    <- read(Keys.profile, "your profile", now)(using Codecs.profileCodec)
      settings   <- read(Keys.settings, "your settings", now)(using Codecs.settingsCodec)
      sessions   <- read(Keys.sessions, "your nights", now)(using Codecs.sessionsCodec)
    yield Loaded(
      Persisted(
        disclaimer._1.getOrElse(false),
        profile._1,
        settings._1.getOrElse(Settings.default),
        sessions._1.getOrElse(Vector.empty).filter(_.intakes.nonEmpty) // a night without intakes has no meaning
      ),
      (if usable then Nil else List(Store.StorageUnavailable)) ++
        List(disclaimer._2, profile._2, settings._2, sessions._2).flatten
    )

  def saveDisclaimer(accepted: Boolean): F[Unit] = kv.set(Keys.disclaimer, accepted.asJson.noSpaces)
  def saveProfile(p: Profile): F[Unit]          = kv.set(Keys.profile, Codecs.profileCodec(p).noSpaces)
  def saveSettings(s: Settings): F[Unit]        = kv.set(Keys.settings, Codecs.settingsCodec(s).noSpaces)
  def saveSessions(ss: Vector[Session]): F[Unit] = kv.set(Keys.sessions, Codecs.sessionsCodec(ss).noSpaces)

  /** Unreadable values are copied to a recovery key and reported; nothing is silently discarded. */
  private def read[A](key: String, what: String, now: Instant)(using d: Decoder[A]): F[(Option[A], Option[String])] =
    kv.get(key).flatMap {
      case None => F.pure((None, None))
      case Some(raw) =>
        parser.decode[A](raw) match
          case Right(a) => F.pure((Some(a), None))
          case Left(e) =>
            val backup = s"oh.recovery.$key.${now.toEpochMilli}"
            // The reason without circe's cursor history ("DecodingFailure at .x: …"), which means nothing to people.
            val reason = e match
              case d: DecodingFailure => d.message
              case other              => other.getMessage
            // Copy, then remove the original so the problem is reported once, not on every launch.
            (kv.set(backup, raw) *> kv.remove(key))
              .as((None, Some(s"Could not read $what, so it was reset. A copy was kept as $backup ($reason).")))
    }

object Store:
  val StorageUnavailable: String =
    "Owl Hours can't save on this device or browser (storage is blocked), so tonight will be forgotten when you close the app."

  object Keys:
    val disclaimer = "oh.v1.disclaimer"
    val profile    = "oh.v1.profile"
    val settings   = "oh.v1.settings"
    val sessions   = "oh.v1.sessions"
