package owlhours.app

import cats.effect.{IO, Resource}
import cats.effect.std.Mutex
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.{Signal, SignallingRef}
import fs2.dom.Window
import java.time.Instant
import org.scalajs.dom
import owlhours.app.logic.{InstallHint, Saving, Sessions}
import owlhours.app.platform.{BrowserKeyValue, CatalogueLoader, Install, Now}
import owlhours.catalogue.Catalogue
import owlhours.domain.*
import owlhours.store.{Persisted, Store}
import scala.concurrent.duration.*

enum Route(val hash: String) {
  case Tonight              extends Route("#/")
  case AddDrink             extends Route("#/add")
  case Water                extends Route("#/water")
  case Food                 extends Route("#/food")
  case Log                  extends Route("#/log")
  case History              extends Route("#/history")
  case Settings             extends Route("#/settings")
  case About                extends Route("#/about")
  case Night(id: SessionId) extends Route(s"#/history/${id.unwrap}")
}

object Route {
  // an enum with a parameterised case has no `values`, so the fixed routes are listed here
  private val fixed = List(AddDrink, Water, Food, Log, History, Settings, About)

  def parse(hash: String): Route =
    if hash.startsWith("#/history/") then SessionId.from(hash.stripPrefix("#/history/")).fold(_ => History, Night(_))
    else fixed.find(r => hash.startsWith(r.hash)).getOrElse(Tonight)
}

enum CatalogueState {
  case Loading
  case Ready(c: Catalogue)
  case Failed(errors: List[String])
}

final case class Undo(message: String, before: Persisted)

final class AppState(
    val persisted: SignallingRef[IO, Persisted],
    val catalogue: SignallingRef[IO, CatalogueState],
    val problems: SignallingRef[IO, List[String]],
    val undo: SignallingRef[IO, Option[Undo]],
    /** Counts whole-state replacements (import, undo), so forms that copy the state can read it afresh. */
    val replaced: SignallingRef[IO, Int],
    val now: Signal[IO, Instant],
    val route: Signal[IO, Route],
    val install: Signal[IO, Option[InstallHint]],
    installDismissed: SignallingRef[IO, Boolean],
    store: Store[IO],
    window: Window[IO],
    lock: Mutex[IO]
) {
  def installHintDismissed: Signal[IO, Boolean] = installDismissed

  /** Hides the Tonight card; remembered when storage allows, and for this visit regardless. */
  def dismissInstallHint: IO[Unit] = installDismissed.set(true) *> store.saveInstallHintDismissed

  def promptInstall: IO[Unit] = Install.prompt

  def acceptDisclaimer: IO[Unit] = persisted.update(_.copy(disclaimerAccepted = true)) *> store.saveDisclaimer(true)

  def saveProfile(p: Profile): IO[Unit] = persisted.update(_.copy(profile = Some(p))) *> store.saveProfile(p)

  /** From Settings: nights are regrouped, because when a night closes depends on the body. */
  def updateProfile(p: Profile): IO[Unit] =
    lock.lock.surround(
      persisted
        .updateAndGet(x => x.copy(profile = Some(p), sessions = Sessions.regroupAll(p, x.sessions)))
        .flatMap(x => store.saveProfile(p) *> store.saveSessions(x.sessions))
    )

  def updateSettings(f: Settings => Settings): IO[Unit] =
    lock.lock.surround(
      persisted.updateAndGet(x => x.copy(settings = f(x.settings))).flatMap(x => store.saveSettings(x.settings))
    )

  /** Replaces everything with a backup's contents, regrouped with its profile; Undo puts the previous data back. */
  def importBackup(next: Persisted): IO[Unit] =
    undoable("Data imported")(cur =>
      next.profile.fold(cur)(p => next.copy(sessions = Sessions.regroupAll(p, next.sessions)))
    ) *>
      replaced.update(_ + 1)

  /** Not undoable on purpose: people who delete their data expect it gone. */
  def deleteEverything: IO[Unit] =
    undo.set(None) *> lock.lock.surround(
      store.clear *> persisted.set(Persisted.empty) *> installDismissed.set(false)
    ) *>
      go(Route.Tonight)

  def addIntake(i: Intake): IO[Unit] = updateSessions((p, ss) => Sessions.add(p, ss, i)) *> undo.set(None)

  def replaceIntake(old: Intake, updated: Intake): IO[Unit] =
    updateSessions((p, ss) => Sessions.replace(p, ss, old, updated)) *> undo.set(None)

  def deleteIntake(old: Intake): IO[Unit] =
    undoable("Entry deleted")(withSessions((p, ss) => Sessions.delete(p, ss, old)))

  def endNight(at: Instant): IO[Unit] = undoable("Night ended")(withSessions((p, ss) => Sessions.end(p, ss, at)))

  def undoLast: IO[Unit] =
    undo
      .getAndSet(None)
      .flatMap(
        _.traverse_(u =>
          lock.lock.surround(persisted.set(u.before) *> store.saveAll(u.before)) *> replaced.update(_ + 1)
        )
      )

  /** A change that offers Undo. The snapshot is taken inside the lock, so it is exactly the state the change applied to. */
  private def undoable(message: String)(f: Persisted => Persisted): IO[Unit] =
    lock.lock.surround(persisted.get.flatMap { before =>
      val after = f(before)
      // only a change that changed something replaces the undo snapshot
      IO.whenA(after != before)(persisted.set(after) *> store.saveAll(after) *> {
        val u = Undo(message, before)
        // a snackbar, not a fixture: it hides itself unless a newer one replaced it
        undo.set(Some(u)) *> (IO.sleep(UndoFor) *> undo.update(_.filterNot(_ eq u))).start.void
      })
    })

  private def withSessions(f: (Profile, Vector[Session]) => Vector[Session])(p: Persisted): Persisted =
    p.profile.fold(p)(profile => p.copy(sessions = f(profile, p.sessions)))

  /** Update-then-save, one at a time: a slow save can never overwrite a newer state. */
  private def saved(f: Persisted => Persisted): IO[Unit] =
    Saving.serialised(persisted, lock, p => store.saveSessions(p.sessions))(f).void

  private def updateSessions(f: (Profile, Vector[Session]) => Vector[Session]): IO[Unit] = saved(withSessions(f))

  private val UndoFor = 8.seconds

  def go(r: Route): IO[Unit] = window.location.hash.set(r.hash)

  def dismissProblems: IO[Unit] = problems.set(Nil)
}

object AppState {
  def resource(window: Window[IO]): Resource[IO, AppState] = {
    val store = Store[IO](BrowserKeyValue(window))
    for {
      loaded    <- store.load.toResource
      persisted <- SignallingRef[IO].of(loaded.persisted).toResource
      catalogue <- SignallingRef[IO].of[CatalogueState](CatalogueState.Loading).toResource
      problems  <- SignallingRef[IO].of(loaded.problems).toResource
      undo      <- SignallingRef[IO].of(Option.empty[Undo]).toResource
      replaced  <- SignallingRef[IO].of(0).toResource
      _ <- CatalogueLoader.load
        .flatMap(r => catalogue.set(r.fold(CatalogueState.Failed(_), CatalogueState.Ready(_))))
        .background
      start <- Now.instant.toResource
      // tick every 30 s, and at once when the app is shown again (timers are paused while a phone sleeps)
      woken = fs2.dom.events[IO, dom.Event](dom.document, "visibilitychange").void
      now   <- Stream.awakeEvery[IO](30.seconds).void.merge(woken).evalMap(_ => Now.instant).holdResource(start)
      hash0 <- window.location.hash.get.toResource
      route <- fs2.dom
        .events[IO, dom.HashChangeEvent](dom.window, "hashchange")
        .evalMap(_ => window.location.hash.get)
        .map(Route.parse)
        .holdResource(Route.parse(hash0))
      // leaving a screen dismisses its undo bar
      _         <- route.discrete.drop(1).foreach(_ => undo.set(None)).compile.drain.background
      dismissed <- store.loadInstallHintDismissed.flatMap(SignallingRef[IO].of).toResource
      install   <- Install.signal.map(_.map(InstallHint.of))
      lock      <- Mutex[IO].toResource
    } yield AppState(
      persisted,
      catalogue,
      problems,
      undo,
      replaced,
      now,
      route,
      install,
      dismissed,
      store,
      window,
      lock
    )
  }
}
