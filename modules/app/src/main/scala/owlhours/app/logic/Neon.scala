package owlhours.app.logic

import owlhours.domain.Format

/**
 * Hand-bent neon tubes (spec §8.1), shared by Watt and the brand drawings. Each tube is drawn twice — a blurred glow
 * and a bright core — so one filter per drawing suffices. Unlit tubes are a single dark stroke.
 */
object Neon {
  enum Tube(val glow: String, val core: String, val unlit: String) {
    case Pink  extends Tube("#FF3F8E", "#FFD2E3", "#7A1F45")
    case Amber extends Tube("#FFB03B", "#FFF0CC", "#A85A0C")
    case Ice   extends Tube("#5CE1FF", "#E8FCFF", "#0E6E86")
    case Warn  extends Tube("#FF6A3D", "#FFD9C9", "#A2401F")
    case White extends Tube("#FFE7C2", "#FFFFFF", "#5C4A3C")
  }

  def n(d: Double): String = Format.number(math.round(d * 100) / 100.0)

  def circ(cx: Double, cy: Double, r: Double): String =
    s"M${n(cx - r)},${n(cy)} a${n(r)},${n(r)} 0 1,0 ${n(2 * r)},0 a${n(r)},${n(r)} 0 1,0 ${n(-2 * r)},0"

  /**
   * Collects glow, core and unlit layers. `off` in `cls` draws one unlit segment (coloured by CSS); `lit = false`
   * draws every tube unlit in its own dark colour, for the daytime "sign off" lockup.
   */
  final class Bender(lit: Boolean = true) {
    private val glows = new StringBuilder
    private val cores = new StringBuilder
    private val unlit = new StringBuilder

    def add(d: String, tube: Tube, cls: String = "", transform: String = ""): Unit = {
      val t = if transform.isEmpty then "" else s""" transform="$transform""""
      if !lit then unlit ++= s"""<path d="$d" stroke="${tube.unlit}"$t/>"""
      else if cls.split(' ').contains("off") then unlit ++= s"""<path d="$d"$t/>"""
      else {
        val c = if cls.isEmpty then "" else s""" class="$cls""""
        glows ++= s"""<path$c d="$d" stroke="${tube.glow}"$t/>"""
        cores ++= s"""<path$c d="$d" stroke="${tube.core}"$t/>"""
      }
    }

    def out(filterId: String): String =
      s"""<g class="watt-unlit">$unlit</g><g class="watt-glows" filter="url(#$filterId)">$glows</g><g class="watt-cores">$cores</g>"""
  }
}
