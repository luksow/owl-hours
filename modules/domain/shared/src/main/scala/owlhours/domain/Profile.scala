package owlhours.domain

enum Sex:
  case Male, Female

/** Body data used by the model. Sex is used only by the blood-volume and body-water formulas. */
final case class Profile(sex: Sex, age: Age, weight: BodyWeight, height: Height)
