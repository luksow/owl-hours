package owlhours.domain

import pl.iterators.kebs.opaque.Opaque

opaque type CatalogueId = String
object CatalogueId extends Opaque[CatalogueId, String]:
  private val Slug = "^[a-z0-9]+(?:-[a-z0-9]+)*$".r
  override protected def validate(x: String): Either[String, CatalogueId] =
    if Slug.matches(x) then Right(x) else Left(s"Catalogue id must be a lowercase slug (a-z, 0-9, -), got $x")

opaque type SessionId = String
object SessionId extends Opaque[SessionId, String]:
  override protected def validate(x: String): Either[String, SessionId] =
    if x.trim.nonEmpty then Right(x) else Left("Session id must not be empty")
