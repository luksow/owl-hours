package owlhours.app.logic

import owlhours.domain.*
import ProfileForm.Fields

class ProfileFormSuite extends munit.FunSuite:
  test("valid input, typed the way people type it") {
    assertEquals(ProfileForm.validate(Fields("female", " 29 ", "64,5", "168")),
      Right(Profile(Sex.Female, Age(29), BodyWeight(64.5), Height(168))))
  }

  test("every problem is reported at once") {
    val errors = ProfileForm.validate(Fields("", "17", "abc", "")).swap.toOption.get.toList
    assertEquals(errors, List(
      "Choose the sex the model should use",
      "Age must be between 18 and 110 years, got 17",
      "Weight must be a number",
      "Height must be a number"
    ))
  }
