package owlhours.domain

import pl.iterators.kebs.opaque.Opaque

opaque type Abv = Double
object Abv extends Opaque[Abv, Double] {
  override protected def validate(x: Double): Either[String, Abv] = Validate.range("ABV", 0, 100, "%")(x)
}

opaque type Millilitres = Double
object Millilitres extends Opaque[Millilitres, Double] {
  override protected def validate(x: Double): Either[String, Millilitres] =
    Validate.range("Volume", 0, 5000, "ml", loExclusive = true)(x)
}

opaque type CarbsPerLitre = Double
object CarbsPerLitre extends Opaque[CarbsPerLitre, Double] {
  override protected def validate(x: Double): Either[String, CarbsPerLitre] =
    Validate.range("Carbohydrates", 0, 300, "g/L")(x)
}

opaque type KcalPerLitre = Double
object KcalPerLitre extends Opaque[KcalPerLitre, Double] {
  override protected def validate(x: Double): Either[String, KcalPerLitre] =
    Validate.range("Energy density", 0, 10000, "kcal/L")(x)
}

opaque type Kcal = Double
object Kcal extends Opaque[Kcal, Double] {
  override protected def validate(x: Double): Either[String, Kcal] = Validate.range("Energy", 0, 5000, "kcal")(x)
}

opaque type BodyWeight = Double
object BodyWeight extends Opaque[BodyWeight, Double] {
  override protected def validate(x: Double): Either[String, BodyWeight] = Validate.range("Weight", 30, 300, "kg")(x)
}

opaque type Height = Double
object Height extends Opaque[Height, Double] {
  override protected def validate(x: Double): Either[String, Height] = Validate.range("Height", 120, 230, "cm")(x)
}

opaque type Age = Int
object Age extends Opaque[Age, Int] {
  override protected def validate(x: Int): Either[String, Age] = Validate.intRange("Age", 18, 110, "years")(x)
}

opaque type Minutes = Double
object Minutes extends Opaque[Minutes, Double] {
  override protected def validate(x: Double): Either[String, Minutes] =
    Validate.range("Duration", 0, 1440, "min")(x)
}
