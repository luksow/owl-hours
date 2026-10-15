package owlhours.app

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.{Signal, SignallingRef}
import fs2.dom.Window
import java.time.Instant
import org.scalajs.dom
import owlhours.app.logic.Sessions
import owlhours.app.platform.{BrowserKeyValue, CatalogueLoader, Now}
import owlhours.catalogue.Catalogue
import owlhours.domain.*
import owlhours.store.{Persisted, Store}
import scala.concurrent.duration.*

enum Route(val hash: String):
  case Tonight  extends Route("#/")
  case AddDrink extends Route("#/add")

object Route:
  def parse(hash: String): Route = if hash.startsWith("#/add") then AddDrink else Tonight

enum CatalogueState:
  case Loading
  case Ready(c: Catalogue)
  case Failed(errors: List[String])

final class AppState(
    val persisted: SignallingRef[IO, Persisted],
    val catalogue: SignallingRef[IO, CatalogueState],
    val problems: SignallingRef[IO, List[String]],
    val now: Signal[IO, Instant],
    val route: Signal[IO, Route],
    store: Store[IO],
    window: Window[IO]
):
  def acceptDisclaimer: IO[Unit] = persisted.update(_.copy(disclaimerAccepted = true)) *> store.saveDisclaimer(true)

  def saveProfile(p: Profile): IO[Unit] = persisted.update(_.copy(profile = Some(p))) *> store.saveProfile(p)

  def addIntake(i: Intake): IO[Unit] =
    for
      now <- Now.instant
      id   = SessionId(s"night-${now.toEpochMilli}")
      next <- persisted.modify { p =>
        p.profile match
          case Some(profile) =>
            val sessions = Sessions.addIntake(profile, p.sessions, i, now, id)
            (p.copy(sessions = sessions), Some(sessions))
          case None => (p, None)
      }
      _ <- next.traverse_(store.saveSessions)
    yield ()

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
      _ <- CatalogueLoader.load
        .flatMap(r => catalogue.set(r.fold(CatalogueState.Failed(_), CatalogueState.Ready(_))))
        .background
      start <- Now.instant.toResource
      now <- (Stream.awakeEvery[IO](30.seconds).evalMap(_ => Now.instant)).holdResource(start)
      hash0 <- window.location.hash.get.toResource
      route <- fs2.dom
        .events[IO, dom.HashChangeEvent](dom.window, "hashchange")
        .evalMap(_ => window.location.hash.get)
        .map(Route.parse)
        .holdResource(Route.parse(hash0))
    yield AppState(persisted, catalogue, problems, now, route, store, window)
