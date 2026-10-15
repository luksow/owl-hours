package owlhours.catalogue

import owlhours.domain.*

enum Group(val label: String):
  case BeerAndCider extends Group("Beer & cider")
  case Wine         extends Group("Wine")
  case Cocktails    extends Group("Cocktails")
  case Spirits      extends Group("Spirits")
  case Liqueurs     extends Group("Liqueurs & aperitifs")
  case Mixers       extends Group("Mixers & soft")

enum Category:
  case Beer, Cider, Seltzer, Wine, Sparkling, Fortified, Sake, Mead, Spirit, Liqueur, Aperitif, Vermouth, Bitters,
    Mixer, Syrup, Cocktail

  def group: Group = this match
    case Beer | Cider | Seltzer                       => Group.BeerAndCider
    case Wine | Sparkling | Fortified | Sake | Mead   => Group.Wine
    case Spirit                                       => Group.Spirits
    case Liqueur | Aperitif | Vermouth | Bitters      => Group.Liqueurs
    case Mixer | Syrup                                => Group.Mixers
    case Cocktail                                     => Group.Cocktails

/** Ice melt added when the drink is mixed, as a fraction of the mixed liquid. Neat = served without ice (neat, up, hot). */
enum Method(val dilution: Double):
  case Neat    extends Method(0.0)
  case Built   extends Method(0.10)
  case Stirred extends Method(0.22)
  case Shaken  extends Method(0.28)
  case Blended extends Method(0.28)

final case class Serving(label: String, volume: Millilitres)

sealed trait Entry:
  def id: CatalogueId
  def name: String
  def category: Category
  def aliases: List[String]
  def source: String
  def estimate: Boolean

sealed trait Ingredient extends Entry:
  def defaultAbv: Abv
  def kcalPerLitre: KcalPerLitre
  def servings: List[Serving]

/** Adjustable drink (beer, wine…): ABV is chosen within a range, kcal follows from ABV and residual carbs. */
final case class Style(
    id: CatalogueId,
    name: String,
    category: Category,
    abv: Abv,
    abvMin: Abv,
    abvMax: Abv,
    carbs: CarbsPerLitre,
    servings: List[Serving],
    aliases: List[String],
    source: String,
    estimate: Boolean
) extends Ingredient:
  def defaultAbv: Abv                     = abv
  def kcalPerLitre: KcalPerLitre          = kcalPerLitreAt(abv)
  def kcalPerLitreAt(a: Abv): KcalPerLitre = KcalPerLitre(Energy.styleKcalPerLitre(a.unwrap, carbs.unwrap))

/** Fixed product (spirit, liqueur, mixer, syrup). Generic spirits allow an ABV override. */
final case class Product(
    id: CatalogueId,
    name: String,
    category: Category,
    abv: Abv,
    kcalPerLitre: KcalPerLitre,
    abvAdjustable: Boolean,
    servings: List[Serving],
    aliases: List[String],
    source: String,
    estimate: Boolean
) extends Ingredient:
  def defaultAbv: Abv = abv

/** `afterMixing`: added after shaking/stirring (e.g. champagne in a French 75), so it gets no ice melt. */
final case class RecipeLine(ingredient: Ingredient, volume: Millilitres, afterMixing: Boolean = false)

/** Cocktail at its standard serve; `solidsKcal` is sugar etc. that adds energy but no meaningful volume. */
final case class Recipe(
    id: CatalogueId,
    name: String,
    method: Method,
    lines: List[RecipeLine],
    solidsKcal: Double,
    aliases: List[String],
    source: String,
    estimate: Boolean
) extends Entry:
  def category: Category = Category.Cocktail
