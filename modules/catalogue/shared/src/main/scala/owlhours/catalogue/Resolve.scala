package owlhours.catalogue

import cats.syntax.all.*
import owlhours.domain.*

enum Choice:
  case OfStyle(style: Style, abv: Abv, volume: Millilitres)
  case OfProduct(product: Product, volume: Millilitres, abvOverride: Option[Abv] = None)
  case OfRecipe(recipe: Recipe, strength: Double = 1.0, volumes: Option[List[Millilitres]] = None, dilution: Boolean = true)

object Resolve:
  val MinStrength: Double = 0.75
  val MaxStrength: Double = 2.0

  def apply(choice: Choice): Either[String, DrinkSnapshot] = choice match
    case Choice.OfStyle(style, abv, volume) =>
      if abv.unwrap < style.abvMin.unwrap || abv.unwrap > style.abvMax.unwrap then
        Left(s"ABV for ${style.name} must be between ${Format.number(style.abvMin.unwrap)} and " +
          s"${Format.number(style.abvMax.unwrap)} %, got ${Format.number(abv.unwrap)}")
      else Right(DrinkSnapshot(Some(style.id), style.name, volume, abv, style.kcalPerLitreAt(abv), Nil))

    case Choice.OfProduct(product, volume, abvOverride) =>
      abvOverride match
        case Some(_) if !product.abvAdjustable => Left(s"${product.name} has a fixed ABV")
        case Some(abv) =>
          KcalPerLitre
            .from(Energy.withAbv(product.kcalPerLitre.unwrap, product.abv.unwrap, abv.unwrap))
            .map(kcal => DrinkSnapshot(Some(product.id), product.name, volume, abv, kcal, Nil))
        case None =>
          Right(DrinkSnapshot(Some(product.id), product.name, volume, product.abv, product.kcalPerLitre, Nil))

    case Choice.OfRecipe(recipe, strength, volumes, dilution) =>
      def checked[A](e: Either[String, A]): Either[String, A] = e.left.map(m => s"${recipe.name}: $m")
      for
        amounts <- amountsFor(recipe, strength, volumes)
        lines     = recipe.lines.zip(amounts)
        liquidMl  = amounts.sum
        mixedMl   = lines.collect { case (l, v) if !l.afterMixing => v }.sum
        ethanolMl = lines.map((l, v) => v * l.ingredient.defaultAbv.unwrap / 100).sum
        solids    = if volumes.isDefined then recipe.solidsKcal else recipe.solidsKcal * strength
        kcal      = lines.map((l, v) => v * l.ingredient.kcalPerLitre.unwrap / 1000).sum + solids
        totalMl   = liquidMl + (if dilution then mixedMl * recipe.method.dilution else 0.0)
        volume     <- checked(Millilitres.from(totalMl))
        abv        <- checked(Abv.from(ethanolMl / totalMl * 100))
        density    <- checked(KcalPerLitre.from(kcal / totalMl * 1000))
        components <- checked(lines.traverse((l, v) => Millilitres.from(v).map(ComponentLine(l.ingredient.name, _, l.ingredient.defaultAbv))))
      yield
        val name =
          if volumes.isDefined then s"${recipe.name} (custom)"
          else if strength != 1.0 then s"${recipe.name} (×${Format.number(strength)})"
          else recipe.name
        val melt = Option.when(totalMl - liquidMl > 0)(totalMl - liquidMl).flatMap(Millilitres.from(_).toOption)
        DrinkSnapshot(Some(recipe.id), name, volume, abv, density, components, melt)

  private def amountsFor(recipe: Recipe, strength: Double, volumes: Option[List[Millilitres]]): Either[String, List[Double]] =
    volumes match
      case Some(vs) if vs.size != recipe.lines.size =>
        Left(s"${recipe.name} needs ${recipe.lines.size} amounts, got ${vs.size}")
      case Some(vs) => Right(vs.map(_.unwrap))
      case None if !(strength >= MinStrength && strength <= MaxStrength) => // also rejects NaN
        Left(s"Strength must be between ×${Format.number(MinStrength)} and ×${Format.number(MaxStrength)}, " +
          s"got ×${Format.number(strength)}")
      case None => Right(recipe.lines.map(_.volume.unwrap * strength))
