package owlhours.app.logic

import owlhours.model.Impairment

class WattSvgSuite extends munit.FunSuite:
  private val all = Impairment.values.toList

  test("every state is a labelled SVG and every state looks different") {
    for i <- all do
      val s = WattSvg.render(i)
      assert(s.startsWith("<svg class=\"watt\""), i.toString)
      assert(s.contains(s"""aria-label="Watt the owl, ${Display.impairment(i)}""""), i.toString)
      assert(s.endsWith("</svg>"), i.toString)
    assertEquals(all.map(WattSvg.render).distinct.size, all.size)
  }

  test("tilt follows the state (the sign hangs crooked as BAC climbs)") {
    val tilts = all.map(i => """rotate\((-?\d+) 100 112\)""".r.findFirstMatchIn(WattSvg.render(i)).map(_.group(1).toInt))
    assertEquals(tilts, List(Some(0), Some(0), Some(-6), Some(8), Some(-10), Some(15), Some(0)))
  }

  test("sober is steady: a gentle hum, no flicker") {
    val s = WattSvg.render(Impairment.Sober)
    assert(s.contains("watt-hum"))
    assert(!s.contains("watt-flick"))
  }

  test("wobbly turns warning orange and flickers hard") {
    val s = WattSvg.render(Impairment.Wobbly)
    assert(s.contains("#FF6A3D") && s.contains("watt-flick3"))
  }

  test("take care is calm: warm white only, upright, no motion, no flicker") {
    val s = WattSvg.render(Impairment.TakeCare)
    for colour <- List("#FF3F8E", "#FFB03B", "#5CE1FF", "#FF6A3D") do assert(!s.contains(colour), colour)
    for cls <- List("watt-flick", "watt-hum", "watt-jitter", "watt-sway", "watt-twinkle", "watt-bob", "watt-rise") do
      assert(!s.contains(cls), cls)
    assert(s.contains("#FFE7C2"))
  }

  test("one glow filter per drawing, with ids that do not clash with the chart") {
    val s = WattSvg.render(Impairment.Buzzed)
    assertEquals("""<filter """.r.findAllMatchIn(s).size, 1)
    assert(s.contains("""id="watt-neon"""") && !s.contains("oh-glow"))
  }
