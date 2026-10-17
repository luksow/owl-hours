package owlhours.app.logic

import cats.data.{EitherNel, NonEmptyList}
import cats.syntax.all.*
import owlhours.catalogue.*
import owlhours.domain.*

/** Per-ingredient amounts for a cocktail; 0 ml leaves an ingredient out. */
object RecipeEdit {
  def defaults(r: Recipe, scale: Double): List[String] =
    r.lines.map(l => Format.number(math.round(l.volume.unwrap * scale * 10) / 10.0))

  def resolve(r: Recipe, amounts: List[String]): EitherNel[String, DrinkSnapshot] =
    r.lines
      .zip(amounts)
      .parTraverse((line, text) => Amount.ingredientMl(text).left.map(e => s"${line.ingredient.name}: $e").toEitherNel)
      .flatMap { volumes =>
        if volumes == r.lines.map(_.volume.unwrap) then Resolve(Choice.OfRecipe(r)).toEitherNel
        else {
          val kept = r.lines.zip(volumes).filter(_._2 > 0)
          if kept.isEmpty then Left(NonEmptyList.one("Add at least one ingredient"))
          else {
            val volumesOk = kept.traverse((_, v) => Millilitres.from(v)).toEitherNel
            volumesOk
              .flatMap(vs => Resolve(Choice.OfRecipe(r.copy(lines = kept.map(_._1)), volumes = Some(vs))).toEitherNel)
          }
        }
      }
}
