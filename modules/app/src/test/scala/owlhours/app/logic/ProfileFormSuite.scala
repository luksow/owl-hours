package owlhours.app.logic

import java.time.LocalDate
import owlhours.domain.*
import ProfileForm.Fields

class ProfileFormSuite extends munit.FunSuite:
  private val today = LocalDate.of(2026, 10, 15)

  test("valid input, typed the way people type it") {
    assertEquals(ProfileForm.validate(Fields("female", "1997-03-02", "64,5", "168"), today),
      Right(Profile(Sex.Female, LocalDate.of(1997, 3, 2), BodyWeight(64.5), Height(168))))
  }

  test("every problem is reported at once") {
    val errors = ProfileForm.validate(Fields("", "", "abc", ""), today).swap.toOption.get.toList
    assertEquals(errors, List(
      "Choose the sex the model should use",
      "Enter your date of birth",
      "Weight must be a number",
      "Height must be a number"
    ))
  }

  test("adults only; birthdays count to the day") {
    assertEquals(ProfileForm.validate(Fields("male", "2008-10-16", "80", "180"), today).swap.toOption.get.toList,
      List("Owl Hours is for adults (18+)"))
    assert(ProfileForm.validate(Fields("male", "2008-10-15", "80", "180"), today).isRight)
    assertEquals(ProfileForm.validate(Fields("male", "1900-01-01", "80", "180"), today).swap.toOption.get.toList,
      List("Age must be between 18 and 110 years, got 126"))
  }
