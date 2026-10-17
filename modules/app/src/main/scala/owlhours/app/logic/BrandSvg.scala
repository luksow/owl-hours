package owlhours.app.logic

import Neon.*

/**
 * The brand drawings of spec §8.6, ported from docs/branding/owl-hours-identity-prototype.html: the owl-head mark,
 * the horizontal lockup (lit for the app, unlit for daytime surfaces) and the two badge plaques. The same head path
 * is in web/public/owl.svg and web/public/icon.svg; e2e/assets.spec.js checks they match.
 */
object BrandSvg:
  val Head = "M30,76 C16,64 18,42 32,32 L24,12 L42,28 Q50,24 58,28 L76,12 L68,32 C82,42 84,64 70,76 C60,84 40,84 30,76 Z"

  /** The prototype's small-glow filter ("neonS"); `id` must be unique in the document. */
  private def glow(id: String): String =
    s"""<filter id="$id" x="-30%" y="-30%" width="160%" height="160%" color-interpolation-filters="sRGB">""" +
      """<feGaussianBlur stdDeviation=".8" result="a"/><feGaussianBlur stdDeviation="2.2" result="b"/>""" +
      """<feGaussianBlur stdDeviation="5" result="c"/><feMerge><feMergeNode in="c"/><feMergeNode in="b"/>""" +
      """<feMergeNode in="a"/><feMergeNode in="SourceGraphic"/></feMerge></filter>"""

  private def mark(b: Bender): Unit =
    b.add(Head, Tube.Pink, "oh-head")
    b.add(circ(39, 52, 10), Tube.Ice); b.add(circ(61, 52, 10), Tube.Ice)
    b.add(circ(39, 52, 2.6), Tube.Ice); b.add(circ(61, 52, 2.6), Tube.Ice)
    b.add("M45.5,62 L50,68 L54.5,62", Tube.Amber)

  def wordmark(lit: Boolean): String =
    val id = "oh-neon-wordmark"
    val b  = Bender(lit)
    mark(b)
    val script = """<text x="152" y="104" font-family="Mr Dafoe, cursive" font-size="92""""
    val name =
      if lit then
        s"""<g filter="url(#$id)">$script fill="#FF3F8E" stroke="#FF3F8E" stroke-width="3" stroke-linejoin="round">Owl Hours</text></g>""" +
          s"""$script fill="#FFD2E3">Owl Hours</text>"""
      else s"""$script fill="${Tube.Pink.unlit}">Owl Hours</text>"""
    val tagColour = if lit then Tube.Amber.glow else Tube.Amber.unlit
    val tagline = s"""<text x="156" y="138" font-family="Barlow Condensed, sans-serif" font-weight="600" font-size="19" """ +
      s"""letter-spacing="4.2" fill="$tagColour">KNOW YOUR CURVE</text>"""
    val defs = if lit then s"<defs>${glow(id)}</defs>" else ""
    s"""<svg class="neon-art" viewBox="0 0 560 160" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Owl Hours">""" +
      s"""$defs<g transform="translate(12 18) scale(1.24)">${b.out(id)}</g>$name$tagline</svg>"""

  /** The glowing name alone, for Tonight's header (identity prototype, section 06). */
  val script: String =
    val id   = "oh-neon-script"
    val text = """<text x="12" y="92" font-family="Mr Dafoe, cursive" font-size="92""""
    s"""<svg class="neon-art" viewBox="0 0 400 120" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Owl Hours">""" +
      s"""<defs>${glow(id)}</defs><g filter="url(#$id)">$text fill="#FF3F8E" stroke="#FF3F8E" stroke-width="3" stroke-linejoin="round">Owl Hours</text></g>""" +
      s"""$text fill="#FFD2E3">Owl Hours</text></svg>"""

  /** A bent-tube plaque for the badges that have art (§8.6: Hydration hero, Slow sipper); the title beside it says the rest. */
  def badge(badge: Badge): Option[String] =
    val drawing: Option[(String, Tube, Bender => Unit)] = badge match
      case Badge.HydrationHero =>
        Some(("oh-neon-hydration", Tube.Ice, b =>
          b.add("M75,36 C60,58 50,70 50,84 a25,25 0 0 0 50,0 C100,70 90,58 75,36 Z", Tube.Ice)
          b.add("M62,86 a13,13 0 0 0 8,12", Tube.Ice)
          b.add("M58,93 q17,-7 34,0 M60,101 q15,-6 30,0", Tube.Ice, "watt-faint")))
      case Badge.SlowSipper =>
        Some(("oh-neon-slow", Tube.Amber, b =>
          b.add("M86,80 a14,14 0 1,0 -10,10 a8,8 0 1,0 6,-6 a3,3 0 1,0 -2,-2", Tube.Amber)
          b.add("M44,96 C34,96 30,86 38,80 C44,76 50,74 54,74 M44,96 H100 M54,74 L48,56 M60,74 L64,56", Tube.Amber)
          b.add(circ(48, 54, 2.2), Tube.Amber); b.add(circ(64, 54, 2.2), Tube.Amber)))
      case _ => None
    drawing.map { (id, tube, draw) =>
      val b = Bender()
      b.add(circ(75, 75, 62), tube); b.add(circ(75, 75, 54), tube, "watt-faint")
      draw(b)
      s"""<svg class="neon-art" viewBox="0 0 150 150" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">""" +
        s"""<defs>${glow(id)}<radialGradient id="$id-spill" cx="50%" cy="50%" r="50%">""" +
        s"""<stop offset="0" stop-color="${tube.glow}" stop-opacity=".2"/><stop offset="1" stop-color="${tube.glow}" stop-opacity="0"/>""" +
        s"""</radialGradient></defs><circle cx="75" cy="75" r="70" fill="url(#$id-spill)"/>${b.out(id)}</svg>"""
    }
