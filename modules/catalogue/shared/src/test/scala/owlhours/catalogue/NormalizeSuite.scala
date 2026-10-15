package owlhours.catalogue

class NormalizeSuite extends munit.FunSuite:
  test("normalize folds case, accents, ampersands and punctuation on every platform") {
    assertEquals(Search.normalize("Żubrówka Bison Grass"), "zubrowka bison grass")
    assertEquals(Search.normalize("G&T"), "g and t")
    assertEquals(Search.normalize("Dark 'n' Stormy"), "dark n stormy")
    assertEquals(Search.normalize("  Crème  de   Mûre "), "creme de mure")
    assertEquals(Search.normalize("Weißbier Ærø Œuvre"), "weissbier aero oeuvre")
  }

  test("review: letters outside the fold table are kept, not dropped") {
    assertEquals(Search.normalize("소주"), "소주")
    assertEquals(Search.normalize("Водка"), "водка")
  }

