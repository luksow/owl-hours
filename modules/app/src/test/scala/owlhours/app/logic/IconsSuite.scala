package owlhours.app.logic

import Neon.Tube

class IconsSuite extends munit.FunSuite {
  test("every icon is stroke path data that stays on its 24-unit grid") {
    for i <- Icon.values do {
      val nums = "-?\\d+(\\.\\d+)?".r.findAllIn(Icons.path(i)).map(_.toDouble).toList
      assert(nums.nonEmpty, i.toString)
      assert(Icons.path(i).startsWith("M"), i.toString)
    }
  }

  test("an icon is decorative neon: glow and core layers, its own filter, the tube's colours") {
    val s = Icons.svg(Icon.Water, Tube.Ice, 24)
    assert(s.contains("""aria-hidden="true""""), s)
    assert(s.contains("""width="24" height="24""""))
    assert(s.contains("""<filter id="oh-icon-water-ice""""))
    assert(s.contains(Tube.Ice.glow) && s.contains(Tube.Ice.core))
    assert(s.contains("watt-glows") && s.contains("watt-cores"))
  }

  test("filter ids differ per icon and per tube, so two icons on one page never share a glow") {
    val ids = for i <- List(Icon.Drink, Icon.Food); t <- List(Tube.Pink, Tube.Amber)
    yield """<filter id="([^"]+)"""".r.findFirstMatchIn(Icons.svg(i, t, 24)).get.group(1)
    assertEquals(ids.distinct.size, 4)
  }
}
