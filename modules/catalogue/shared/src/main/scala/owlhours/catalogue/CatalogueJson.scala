package owlhours.catalogue

import io.circe.Decoder
import owlhours.domain.*
import pl.iterators.kebs.circe.*

/** Wire format of catalogue.json. Opaque fields (Abv, Millilitres…) decode through their kebs validators. */
private[catalogue] object CatalogueJson:
  final case class ServingJson(label: String, ml: Millilitres)
  final case class StyleJson(
      id: CatalogueId,
      name: String,
      category: Category,
      abv: Abv,
      abvMin: Abv,
      abvMax: Abv,
      carbsPerLitre: CarbsPerLitre,
      servings: List[ServingJson],
      aliases: List[String],
      source: String,
      estimate: Boolean
  )
  final case class ProductJson(
      id: CatalogueId,
      name: String,
      category: Category,
      abv: Abv,
      kcalPer100ml: Double,
      abvAdjustable: Boolean,
      servings: List[ServingJson],
      aliases: List[String],
      source: String,
      estimate: Boolean
  )
  final case class IngredientJson(id: CatalogueId, ml: Millilitres, afterMixing: Option[Boolean])
  final case class CocktailJson(
      id: CatalogueId,
      name: String,
      method: Method,
      ingredients: List[IngredientJson],
      solidsKcal: Double,
      aliases: List[String],
      source: String,
      estimate: Boolean
  )
  final case class CatalogueFile(
      schemaVersion: Int,
      styles: List[StyleJson],
      products: List[ProductJson],
      cocktails: List[CocktailJson]
  )

  private def byName[E](values: Array[E], what: String): Decoder[E] =
    Decoder.decodeString.emap(s => values.find(_.toString == s).toRight(s"unknown $what '$s'"))

  given Decoder[Category] = byName(Category.values, "category")
  given Decoder[Method]   = byName(Method.values, "method")

  val fileDecoder: Decoder[CatalogueFile] = summon[Decoder[CatalogueFile]]
