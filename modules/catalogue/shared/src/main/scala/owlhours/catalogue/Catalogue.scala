package owlhours.catalogue

import cats.data.{NonEmptyList, ValidatedNel}
import cats.syntax.all.*
import io.circe.{CursorOp, DecodingFailure, ParsingFailure, parser}
import owlhours.domain.*
import CatalogueJson.*

final case class Catalogue(schemaVersion: Int, styles: List[Style], products: List[Product], recipes: List[Recipe]) {
  val all: List[Entry]              = styles ++ products ++ recipes
  val byId: Map[CatalogueId, Entry] = all.map(e => e.id -> e).toMap

  def get(id: CatalogueId): Option[Entry] = byId.get(id)
  def inGroup(g: Group): List[Entry]       = all.filter(_.category.group == g)
}

object Catalogue {
  val SchemaVersion: Int = 1

  /** Rounding allowance when comparing label energy with the alcohol floor (3 kcal per 100 ml). */
  private val FloorToleranceKcalPerLitre = 30.0

  /** Upper bound for sugar etc. added as solids to one cocktail (≈ 250 g of sugar). */
  private val MaxSolidsKcal = 1000.0

  def fromJson(json: String): Either[NonEmptyList[String], Catalogue] =
    parser
      .decodeAccumulating[CatalogueFile](json)(using fileDecoder)
      .leftMap(_.map(describe))
      .toEither
      .flatMap(link)

  private def describe(e: io.circe.Error): String = e match {
    case p: ParsingFailure  => s"catalogue is not valid JSON: ${p.message}"
    case d: DecodingFailure => s"${CursorOp.opsToPath(d.history)}: ${d.message}"
  }

  private def link(f: CatalogueFile): Either[NonEmptyList[String], Catalogue] = {
    val version: ValidatedNel[String, Unit] =
      if f.schemaVersion == SchemaVersion then ().validNel
      else s"unsupported schemaVersion ${f.schemaVersion} (expected $SchemaVersion)".invalidNel

    val styles = f.styles.traverse { s =>
      val inRange = s.abvMin.unwrap <= s.abv.unwrap && s.abv.unwrap <= s.abvMax.unwrap
      if inRange then
        Style(s.id, s.name, s.category, s.abv, s.abvMin, s.abvMax, s.carbsPerLitre, servings(s.servings), s.aliases,
          s.source, s.estimate).validNel
      else
        (s"styles[${s.id.unwrap}]: default ABV ${Format.number(s.abv.unwrap)} is outside its range " +
          s"${Format.number(s.abvMin.unwrap)}–${Format.number(s.abvMax.unwrap)}").invalidNel
    }

    val products = f.products.traverse { p =>
      val floor = Energy.ethanolKcalPerLitre(p.abv.unwrap)
      KcalPerLitre.from(p.kcalPer100ml * 10).leftMap(e => s"products[${p.id.unwrap}]: $e").toValidatedNel.andThen {
        kcal =>
          if kcal.unwrap >= floor - FloorToleranceKcalPerLitre then
            Product(p.id, p.name, p.category, p.abv, kcal, p.abvAdjustable, servings(p.servings), p.aliases, p.source,
              p.estimate).validNel
          else
            (s"products[${p.id.unwrap}]: ${Format.number(p.kcalPer100ml)} kcal/100 ml is below the energy of its " +
              s"alcohol alone (${Format.number(math.round(floor) / 10.0)})").invalidNel
      }
    }

    val ids        = f.styles.map(_.id) ++ f.products.map(_.id) ++ f.cocktails.map(_.id)
    val duplicates = ids.groupBy(identity).collect { case (id, xs) if xs.size > 1 => id.unwrap }.toList.sorted
    val unique: ValidatedNel[String, Unit] =
      duplicates.traverse_(id => s"duplicate id '$id'".invalidNel[Unit])

    val cocktailShape: ValidatedNel[String, Unit] = f.cocktails.traverse_ { c =>
      val nonEmpty =
        if c.ingredients.nonEmpty then ().validNel else s"cocktails[${c.id.unwrap}]: needs at least one ingredient".invalidNel
      val solids =
        if c.solidsKcal >= 0 && c.solidsKcal <= MaxSolidsKcal then ().validNel
        else s"cocktails[${c.id.unwrap}]: solidsKcal must be between 0 and ${Format.number(MaxSolidsKcal)}, got ${Format.number(c.solidsKcal)}".invalidNel
      nonEmpty |+| solids
    }

    val ingredientIds = (f.styles.map(_.id) ++ f.products.map(_.id)).toSet
    val references: ValidatedNel[String, Unit] = f.cocktails.traverse_ { c =>
      c.ingredients.traverse_ { i =>
        if ingredientIds(i.id) then ().validNel
        else s"cocktails[${c.id.unwrap}]: unknown ingredient '${i.id.unwrap}'".invalidNel
      }
    }

    (version, styles, products, unique, references, cocktailShape).mapN { (_, ss, ps, _, _, _) =>
      val ingredients: Map[CatalogueId, Ingredient] = (ss ++ ps).map(i => i.id -> i).toMap
      val recipes = f.cocktails.map { c =>
        Recipe(c.id, c.name, c.method, c.ingredients.map(i => RecipeLine(ingredients(i.id), i.ml, i.afterMixing.getOrElse(false))), c.solidsKcal,
          c.aliases, c.source, c.estimate)
      }
      Catalogue(f.schemaVersion, ss, ps, recipes)
    }.toEither.flatMap(resolvesAtStandardServe)
  }

  /** The loader is the single gate: a cocktail that cannot be resolved at its standard serve does not load. */
  private def resolvesAtStandardServe(c: Catalogue): Either[NonEmptyList[String], Catalogue] =
    c.recipes
      .traverse_(r => Resolve(Choice.OfRecipe(r)).leftMap(e => s"cocktails[${r.id.unwrap}]: $e").toValidatedNel)
      .toEither
      .as(c)

  private def servings(xs: List[ServingJson]): List[Serving] = xs.map(s => Serving(s.label, s.ml))
}
