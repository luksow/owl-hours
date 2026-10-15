package owlhours.catalogue

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
          val kcal = Energy.withAbv(product.kcalPerLitre.unwrap, product.abv.unwrap, abv.unwrap)
          Right(DrinkSnapshot(Some(product.id), product.name, volume, abv, KcalPerLitre(kcal), Nil))
        case None =>
          Right(DrinkSnapshot(Some(product.id), product.name, volume, product.abv, product.kcalPerLitre, Nil))

    case Choice.OfRecipe(recipe, strength, volumes, dilution) =>
      for
        amounts <- amountsFor(recipe, strength, volumes)
      yield
        val lines     = recipe.lines.zip(amounts)
        val liquidMl  = amounts.sum
        val ethanolMl = lines.map((l, v) => v * l.ingredient.defaultAbv.unwrap / 100).sum
        val solids    = if volumes.isDefined then recipe.solidsKcal else recipe.solidsKcal * strength
        val kcal      = lines.map((l, v) => v * l.ingredient.kcalPerLitre.unwrap / 1000).sum + solids
        val mixedMl   = lines.collect { case (l, v) if !l.afterMixing => v }.sum
        val totalMl   = liquidMl + (if dilution then mixedMl * recipe.method.dilution else 0.0)
        val name =
          if volumes.isDefined then s"${recipe.name} (custom)"
          else if strength != 1.0 then s"${recipe.name} (×${Format.number(strength)})"
          else recipe.name
        DrinkSnapshot(
          Some(recipe.id),
          name,
          Millilitres(totalMl),
          Abv(ethanolMl / totalMl * 100),
          KcalPerLitre(kcal / totalMl * 1000),
          lines.map((l, v) => ComponentLine(l.ingredient.name, Millilitres(v), l.ingredient.defaultAbv))
        )

  private def amountsFor(recipe: Recipe, strength: Double, volumes: Option[List[Millilitres]]): Either[String, List[Double]] =
    volumes match
      case Some(vs) if vs.size != recipe.lines.size =>
        Left(s"${recipe.name} needs ${recipe.lines.size} amounts, got ${vs.size}")
      case Some(vs) => Right(vs.map(_.unwrap))
      case None if strength < MinStrength || strength > MaxStrength =>
        Left(s"Strength must be between ×${Format.number(MinStrength)} and ×${Format.number(MaxStrength)}, " +
          s"got ×${Format.number(strength)}")
      case None => Right(recipe.lines.map(_.volume.unwrap * strength))
