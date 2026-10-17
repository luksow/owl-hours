package owlhours.domain

import java.time.{Instant, LocalDate, Period, ZoneOffset}

enum Sex {
  case Male, Female
}

/** Body data used by the model. Sex is used only by the blood-volume and body-water formulas. */
final case class Profile(sex: Sex, born: LocalDate, weight: BodyWeight, height: Height) {

  /** Whole years at `at`, using the UTC date (at most a day off on the birthday; the model is insensitive to that). */
  def ageAt(at: Instant): Int = Period.between(born, at.atOffset(ZoneOffset.UTC).toLocalDate).getYears
}
