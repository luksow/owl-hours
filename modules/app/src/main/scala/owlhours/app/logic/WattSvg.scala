package owlhours.app.logic

import owlhours.model.Impairment
import Neon.*

/**
 * Watt, the hand-bent neon owl (spec §8.1), ported from docs/branding/owl-hours-identity-prototype.html.
 * Each tube is drawn twice — a blurred glow and a bright core — so one filter per drawing suffices; unlit tubes are
 * dark. Motion classes are defined in web/main.css and respect prefers-reduced-motion.
 */
object WattSvg {

  /** The lower part of an eye below a lid at `lidY`, plus the faint upper remainder. */
  private def lidEye(b: Bender, cx: Double, cy: Double, r: Double, lidY: Double, tube: Tube): Unit = {
    val dy    = lidY - cy
    val dx    = math.round(math.sqrt(r * r - dy * dy) * 100) / 100.0
    val large = if lidY < cy then 1 else 0
    b.add(s"M${n(cx - dx)},${n(lidY)} A${n(r)},${n(r)} 0 $large,0 ${n(cx + dx)},${n(lidY)} Z", tube)
    b.add(s"M${n(cx - dx)},${n(lidY)} A${n(r)},${n(r)} 0 ${1 - large},1 ${n(cx + dx)},${n(lidY)}", tube, "watt-faint")
  }

  private def star(x: Double, y: Double, s: Double): String =
    s"M${n(x)},${n(y - s)} V${n(y + s)} M${n(x - s)},${n(y)} H${n(x + s)}"

  private val Spiral =
    "M1.0,0.0 L1.2,0.2 L1.3,0.5 L1.4,0.9 L1.3,1.3 L1.2,1.7 L0.9,2.1 L0.5,2.4 L0.1,2.7 L-0.5,2.8 L-1.1,2.9 L-1.7,2.8 " +
      "L-2.4,2.6 L-3.0,2.2 L-3.6,1.6 L-4.0,1.0 L-4.3,0.2 L-4.5,-0.7 L-4.5,-1.6 L-4.3,-2.5 L-3.8,-3.5 L-3.2,-4.3 " +
      "L-2.4,-5.0 L-1.5,-5.6 L-0.4,-6.0 L0.8,-6.2 L2.0,-6.1 L3.3,-5.8 L4.5,-5.2 L5.5,-4.3 L6.5,-3.3 L7.2,-2.0 " +
      "L7.6,-0.6 L7.8,0.9 L7.7,2.4 L7.3,4.0 L6.5,5.4 L5.5,6.7 L4.2,7.9 L2.7,8.7 L1.0,9.3 L-0.8,9.5 L-2.7,9.4 " +
      "L-4.6,8.8 L-6.3,8.0 L-7.9,6.7 L-9.2,5.2 L-10.2,3.4 L-10.9,1.4 L-11.2,-0.8 L-11.0,-3.0 L-10.4,-5.1 " +
      "L-9.4,-7.2 L-8.0,-9.0 L-6.2,-10.5 L-4.2,-11.7 L-1.9,-12.5 L0.6,-12.9 L3.1,-12.7 L5.6,-12.1 L7.9,-10.9"
  private val Body =
    "M100,194 C72,194 52,172 52,142 C52,118 60,100 72,88 L60,54 L82,76 Q100,68 118,76 L140,54 L128,88 " +
      "C140,100 148,118 148,142 C148,172 128,194 100,194 Z"
  private val WingL    = "M70,106 C58,130 60,160 74,182"
  private val WingR    = "M130,106 C142,130 140,160 126,182"
  private val WingROut = "M132,106 C150,114 160,132 161,150"
  private val Belly    = "M78,158 q11,-12 22,0 q11,-12 22,0 M84,172 q8,-10 16,0 q8,-10 16,0"
  private val Feet     = "M84,195 l-4,7 M90,195 v7 M96,195 l4,7 M104,195 l-4,7 M110,195 v7 M116,195 l4,7"
  private val Tie      = s"M100,147 L90,141.5 L90,152.5 Z M100,147 L110,141.5 L110,152.5 Z ${circ(100, 147, 2.2)}"
  private val Cheeks   = ("M60,126 q7,5 14,0 M62,131 q6,4 12,0", "M126,126 q7,5 14,0 M126,131 q6,4 12,0")

  private final case class Look(
      tilt: Int,
      motion: String,
      body: Tube,
      spill: String,
      draw: Bender => Unit,
      bodyCls: String = "",
      bellyCls: String = "",
      wingL: String = "",
      wingR: String = "",
      care: Boolean = false
  )

  private def look(i: Impairment): Look = i match {
    case Impairment.Sober =>
      Look(
        0,
        "watt-hum",
        Tube.Pink,
        Tube.Pink.glow,
        b => {
          b.add(circ(82, 110, 15), Tube.Ice); b.add(circ(118, 110, 15), Tube.Ice)
          b.add(circ(82, 110, 4), Tube.Ice); b.add(circ(118, 110, 4), Tube.Ice)
          b.add("M93,124 L100,133 L107,124", Tube.Amber)
          b.add(Tie, Tube.Amber)
        }
      )
    case Impairment.WarmingUp =>
      Look(
        0,
        "watt-hum",
        Tube.Pink,
        Tube.Pink.glow,
        b => {
          b.add(circ(82, 110, 15), Tube.Ice); b.add(circ(118, 110, 15), Tube.Ice)
          b.add(circ(84, 111, 4.8), Tube.Ice); b.add(circ(120, 111, 4.8), Tube.Ice)
          b.add("M66,88 q12,-9 24,-3", Tube.Ice)
          b.add("M92,124 Q100,137 108,124", Tube.Amber)
          b.add(Tie, Tube.Amber)
          b.add(star(160, 74, 5), Tube.Ice, "watt-twinkle")
        }
      )
    case Impairment.Tipsy =>
      Look(
        -6,
        "watt-hum",
        Tube.Pink,
        Tube.Pink.glow,
        wingL = "watt-flick1",
        draw = b => {
          b.add(circ(82, 110, 15), Tube.Ice); b.add(circ(84, 112, 4.5), Tube.Ice)
          lidEye(b, 118, 110, 15, 104, Tube.Ice); b.add(circ(119, 114, 4), Tube.Ice)
          b.add("M91,124 Q100,139 109,124", Tube.Amber)
          b.add(Tie, Tube.Amber, transform = "rotate(-9 100 146)")
          b.add("M62,126 q7,5 14,0", Tube.Pink); b.add("M124,126 q7,5 14,0", Tube.Pink)
        }
      )
    case Impairment.Buzzed =>
      Look(
        8,
        "watt-jitter",
        Tube.Pink,
        Tube.Pink.glow,
        bellyCls = "watt-flick1",
        draw = b => {
          lidEye(b, 82, 110, 15, 106, Tube.Ice); lidEye(b, 118, 110, 15, 106, Tube.Ice)
          b.add(circ(80, 114, 4), Tube.Ice); b.add(circ(121, 113, 4), Tube.Ice)
          b.add(circ(100, 131, 5.5), Tube.Amber)
          b.add(Tie, Tube.Amber, transform = "rotate(18 100 146)")
          b.add(Cheeks._1, Tube.Pink); b.add(Cheeks._2, Tube.Pink)
          b.add(s"M160,64 v18 M160,64 l8,3 ${circ(157, 83, 3.5)}", Tube.Ice, "watt-bob")
          b.add(s"M176,78 v14 M176,78 l7,3 ${circ(173.5, 93, 3)}", Tube.Ice, "watt-bob2")
        }
      )
    case Impairment.Drunk =>
      Look(
        -10,
        "watt-sway",
        Tube.Pink,
        Tube.Pink.glow,
        bellyCls = "watt-flick2",
        wingL = "off",
        draw = b => {
          b.add(Spiral, Tube.Ice, transform = "translate(82 110)")
          b.add(Spiral, Tube.Ice, transform = "translate(118 110) scale(-1 1)")
          b.add(circ(100, 132, 6.5), Tube.Amber)
          b.add(
            s"M100,147 L90,141.5 L90,152.5 Z ${circ(100, 147, 2.2)} M100,147 L107,163",
            Tube.Amber,
            transform = "rotate(-12 100 147)"
          )
          b.add(Cheeks._1, Tube.Pink); b.add(Cheeks._2, Tube.Pink)
          b.add(circ(121, 96, 2), Tube.Ice, "watt-rise"); b.add(circ(130, 86, 3), Tube.Ice, "watt-rise2")
          b.add(circ(141, 74, 4), Tube.Ice, "watt-rise3")
        }
      )
    case Impairment.Wobbly =>
      Look(
        15,
        "watt-sway2",
        Tube.Warn,
        Tube.Warn.glow,
        bodyCls = "watt-flick3",
        bellyCls = "watt-flick2",
        wingL = "watt-faint",
        wingR = "off",
        draw = b => {
          lidEye(b, 82, 110, 15, 114, Tube.Ice); lidEye(b, 118, 113, 15, 118, Tube.Ice)
          b.add(circ(80, 117.5, 2.5), Tube.Ice); b.add(circ(121, 121, 2.5), Tube.Ice)
          b.add("M92,127 q4,-5 8,0 q4,5 8,0", Tube.Amber)
          b.add(Tie, Tube.Amber, "off")
          b.add(star(48, 40, 5), Tube.Amber, "watt-twinkle"); b.add(star(100, 26, 4), Tube.Amber, "watt-twinkle2")
          b.add(star(152, 46, 6), Tube.Amber, "watt-twinkle3")
        }
      )
    case Impairment.TakeCare =>
      Look(
        0,
        "",
        Tube.White,
        Tube.White.glow,
        care = true,
        draw = b => {
          b.add(circ(82, 111, 15), Tube.White); b.add(circ(118, 111, 15), Tube.White)
          b.add(circ(82, 112, 5.5), Tube.White); b.add(circ(118, 112, 5.5), Tube.White)
          b.add("M68,88 q10,-7 20,-3", Tube.White); b.add("M132,88 q-10,-7 -20,-3", Tube.White)
          b.add("M94,125 L100,132 L106,125", Tube.White)
          b.add(Tie, Tube.White)
          b.add("M153,150 l3,24 h12 l3,-24 Z M156,161 h12", Tube.White)
        }
      ) // the glass of water
  }

  private val Defs =
    """<filter id="watt-neon" x="-30%" y="-30%" width="160%" height="160%" color-interpolation-filters="sRGB">""" +
      """<feGaussianBlur stdDeviation="1.4" result="a"/><feGaussianBlur stdDeviation="4" result="b"/>""" +
      """<feGaussianBlur stdDeviation="9" result="c"/><feMerge><feMergeNode in="c"/><feMergeNode in="b"/>""" +
      """<feMergeNode in="a"/><feMergeNode in="SourceGraphic"/></feMerge></filter>""" +
      """<pattern id="watt-brick" width="40" height="20" patternUnits="userSpaceOnUse"><rect width="40" height="20" fill="#241219"/>""" +
      """<rect x="1" y="1" width="18" height="8" rx="1" fill="#2E1A22"/><rect x="21" y="1" width="18" height="8" rx="1" fill="#2B1820"/>""" +
      """<rect x="-9" y="11" width="18" height="8" rx="1" fill="#2C1921"/><rect x="11" y="11" width="18" height="8" rx="1" fill="#301B24"/>""" +
      """<rect x="31" y="11" width="18" height="8" rx="1" fill="#2A171F"/></pattern>""" +
      """<radialGradient id="watt-vig" cx="50%" cy="45%" r="72%"><stop offset="0" stop-color="#000" stop-opacity="0"/>""" +
      """<stop offset="1" stop-color="#000" stop-opacity=".62"/></radialGradient>"""

  /** `wall = false` leaves out the brick and vignette, for a page that draws the wall itself (Tonight's sign panel). */
  def render(i: Impairment, wall: Boolean = true): String = {
    val l      = look(i)
    val accent = if l.care then Tube.White else Tube.Amber
    val wings  = if l.care then Tube.White else Tube.Pink
    val b      = Bender()
    b.add("M30,201 H170", accent); b.add(Feet, accent)
    b.add(Body, l.body, l.bodyCls)
    b.add(WingL, wings, l.wingL)
    b.add(if l.care then WingROut else WingR, wings, l.wingR)
    b.add(Belly, accent, l.bellyCls)
    l.draw(b)
    val spill =
      s"""<radialGradient id="watt-spill" cx="50%" cy="50%" r="50%"><stop offset="0" stop-color="${l.spill}" stop-opacity=".2"/>""" +
        s"""<stop offset="1" stop-color="${l.spill}" stop-opacity="0"/></radialGradient>"""
    val motion = if l.motion.isEmpty then "" else s""" class="${l.motion}""""
    s"""<svg class="watt" viewBox="0 0 200 220" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Watt the owl, ${Display
        .impairment(i)}">""" +
      s"""<defs>$Defs$spill</defs>""" +
      (if wall then
         """<rect width="200" height="220" fill="url(#watt-brick)"/><rect width="200" height="220" fill="url(#watt-vig)"/>"""
       else "") +
      s"""<ellipse cx="100" cy="124" rx="96" ry="100" fill="url(#watt-spill)"/>""" +
      s"""<g transform="rotate(${l.tilt} 100 112)"><g$motion style="transform-origin:100px 112px">${b.out(
          "watt-neon"
        )}</g></g></svg>"""
  }
}
