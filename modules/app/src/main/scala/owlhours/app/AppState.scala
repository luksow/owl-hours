package owlhours.app

import cats.effect.{IO, Resource}
import cats.effect.std.Mutex
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.{Signal, SignallingRef}
import fs2.dom.Window
import java.time.Instant
import org.scalajs.dom
import owlhours.app.logic.{Saving, Sessions}
import owlhours.app.platform.{BrowserKeyValue, CatalogueLoader, Now}
import owlhours.catalogue.Catalogue
import owlhours.domain.*
import owlhours.store.{Persisted, Store}
import scala.concurrent.duration.*

enum Route(val hash: String):
  case Tonight  extends Route("#/")
  case AddDrink extends Route("#/add")
  case Water    extends Route("#/water")
  case Food     extends Route("#/food")
  case Log      extends Route("#/log")

object Route:
  def parse(hash: String): Route = Route.values.filter(_ != Tonight).find(r => hash.startsWith(r.hash)).getOrElse(Tonight)

enum CatalogueState:
  case Loading
  case Ready(c: Catalogue)
  case Failed(errors: List[String])

final case class Undo(message: String, sessions: Vector[Session])

final class AppState(
    val persisted: SignallingRef[IO, Persisted],
    val catalogue: SignallingRef[IO, CatalogueState],
    val problems: SignallingRef[IO, List[String]],
    val undo: SignallingRef[IO, Option[Undo]],
    val now: Signal[IO, Instant],
    val route: Signal[IO, Route],
    store: Store[IO],
    window: Window[IO],
    lock: Mutex[IO]
):
  def acceptDisclaimer: IO[Unit] = persisted.update(_.copy(disclaimerAccepted = true)) *> store.saveDisclaimer(true)

  def saveProfile(p: Profile): IO[Unit] = persisted.update(_.copy(profile = Some(p))) *> store.saveProfile(p)

  def addIntake(i: Intake): IO[Unit] = updateSessions((p, ss) => Sessions.add(p, ss, i)) *> undo.set(None)

  def replaceIntake(old: Intake, updated: Intake): IO[Unit] =
    updateSessions((p, ss) => Sessions.replace(p, ss, old, updated)) *> undo.set(None)

  def deleteIntake(old: Intake): IO[Unit] =
    // the snapshot for undo is taken inside the lock, so it is exactly the state the delete applied to
    lock.lock.surround(persisted.get.flatMap { before =>
      // only a delete that removed something replaces the undo snapshot
      IO.whenA(before.sessions.exists(_.intakes.contains(old)))(
        persisted.updateAndGet(withSessions((p, ss) => Sessions.delete(p, ss, old))).flatMap(p => store.saveSessions(p.sessions)) *> {
          val u = Undo("Entry deleted", before.sessions)
          // a snackbar, not a fixture: it hides itself unless a newer one replaced it
          undo.set(Some(u)) *> (IO.sleep(UndoFor) *> undo.update(_.filterNot(_ eq u))).start.void
        })
    })

  def undoLast: IO[Unit] =
    undo.getAndSet(None).flatMap(_.traverse_(u => saved(_.copy(sessions = u.sessions))))

  private def withSessions(f: (Profile, Vector[Session]) => Vector[Session])(p: Persisted): Persisted =
    p.profile.fold(p)(profile => p.copy(sessions = f(profile, p.sessions)))

  /** Update-then-save, one at a time: a slow save can never overwrite a newer state. */
  private def saved(f: Persisted => Persisted): IO[Unit] =
    Saving.serialised(persisted, lock, p => store.saveSessions(p.sessions))(f).void

  private def updateSessions(f: (Profile, Vector[Session]) => Vector[Session]): IO[Unit] = saved(withSessions(f))

  private val UndoFor = 8.seconds

  def go(r: Route): IO[Unit] = window.location.hash.set(r.hash)

  def dismissProblems: IO[Unit] = problems.set(Nil)

object AppState:
  def resource(window: Window[IO]): Resource[IO, AppState] =
    val store = Store[IO](BrowserKeyValue(window))
    for
      loaded    <- store.load.toResource
      persisted <- SignallingRef[IO].of(loaded.persisted).toResource
      catalogue <- SignallingRef[IO].of[CatalogueState](CatalogueState.Loading).toResource
      problems  <- SignallingRef[IO].of(loaded.problems).toResource
      undo      <- SignallingRef[IO].of(Option.empty[Undo]).toResource
      _ <- CatalogueLoader.load
        .flatMap(r => catalogue.set(r.fold(CatalogueState.Failed(_), CatalogueState.Ready(_))))
        .background
      start <- Now.instant.toResource
      // tick every 30 s, and at once when the app is shown again (timers are paused while a phone sleeps)
      woken = fs2.dom.events[IO, dom.Event](dom.document, "visibilitychange").void
      now <- Stream.awakeEvery[IO](30.seconds).void.merge(woken).evalMap(_ => Now.instant).holdResource(start)
      hash0 <- window.location.hash.get.toResource
      route <- fs2.dom
        .events[IO, dom.HashChangeEvent](dom.window, "hashchange")
        .evalMap(_ => window.location.hash.get)
        .map(Route.parse)
        .holdResource(Route.parse(hash0))
      // leaving a screen dismisses its undo bar
      _ <- route.discrete.drop(1).foreach(_ => undo.set(None)).compile.drain.background
      lock <- Mutex[IO].toResource
    yield AppState(persisted, catalogue, problems, undo, now, route, store, window, lock)
