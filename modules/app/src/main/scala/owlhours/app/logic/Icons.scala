package owlhours.app.logic

import Neon.*

/** The app's pictograms (spec §5): stroke paths on a 24-unit grid, drawn as neon tubes like Watt. */
enum Icon {
  case Drink, Repeat, Water, Food, Log, History, Settings, Beer, Wine, Shot
}

object Icons {
  def path(icon: Icon): String = icon match {
    // Drink, Food and Settings are traced from Recraft candidates (Plan 8, Task 4); the others are hand-drawn
    case Icon.Drink    => "M4.5 7.5h15c-.8 3.5-3.8 5.8-7.5 5.8s-6.7-2.3-7.5-5.8z M12 13.3v6 M8 19.8h8 M11 11l3.5-7h3.5"
    case Icon.Repeat   => "M17 3l3 3-3 3 M20 6H9a4 4 0 0 0-4 4v1 M7 21l-3-3 3-3 M4 18h11a4 4 0 0 0 4-4v-1"
    case Icon.Water    => "M12 3c-4 5.5-6.5 8.8-6.5 12a6.5 6.5 0 0 0 13 0c0-3.2-2.5-6.5-6.5-12z M9 15.5a3 3 0 0 0 2 3"
    case Icon.Food     =>
      "M5 10.3c1-3 3.6-4.6 7-4.6s6 1.6 7 4.6z M3.5 12.6c1.5-1 1.5-1 3 0s1.5 1 3 0 1.5-1 3 0 1.5 1 3 0 1.5-1 3 0 1.5 1 2.5 0 M5 15h14c-1 2.6-3.6 3.8-7 3.8s-6-1.2-7-3.8z"
    case Icon.Log      => "M9 6h11 M9 12h11 M9 18h11 M4.5 6h.5 M4.5 12h.5 M4.5 18h.5"
    case Icon.History  => "M3.5 12a8.5 8.5 0 1 0 2.5-6 M3.5 3.5v4.5h4.5 M12 7.5v4.5l3 2"
    case Icon.Settings =>
      "M9.84 4.92L10.19 2.67L13.81 2.67L14.16 4.92L15.47 5.47L17.31 4.12L19.88 6.69L18.53 8.53L19.08 9.84L21.33 10.19L21.33 13.81" +
        "L19.08 14.16L18.53 15.47L19.88 17.31L17.31 19.88L15.47 18.53L14.16 19.08L13.81 21.33L10.19 21.33L9.84 19.08L8.53 18.53" +
        s"L6.69 19.88L4.12 17.31L5.47 15.47L4.92 14.16L2.67 13.81L2.67 10.19L4.92 9.84L5.47 8.53L4.12 6.69L6.69 4.12L8.53 5.47z ${circ(12, 12, 3.6)}"
    case Icon.Beer     => "M5 8h10v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2z M15 10h2.5a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H15 M5 8c0-3 2.5-4 5-3 2-2 5-1 5 3 M8.5 12v6M11.5 12v6"
    case Icon.Wine     => "M7 3h10c0 6-2 9.5-5 9.5S7 9 7 3z M12 12.5V20M8 20h8M7.6 7h8.8"
    case Icon.Shot     => "M7 4h10l-1.6 16H8.6z M8 11h8"
  }

  private def glow(id: String): String =
    s"""<filter id="$id" x="-50%" y="-50%" width="200%" height="200%" color-interpolation-filters="sRGB">""" +
      """<feGaussianBlur stdDeviation=".6" result="a"/><feGaussianBlur stdDeviation="1.6" result="b"/>""" +
      """<feMerge><feMergeNode in="b"/><feMergeNode in="a"/><feMergeNode in="SourceGraphic"/></feMerge></filter>"""

  def svg(icon: Icon, tube: Tube, px: Int): String = {
    val id = s"oh-icon-${icon.toString.toLowerCase}-${tube.toString.toLowerCase}"
    val b  = Bender()
    b.add(path(icon), tube)
    s"""<svg class="neon-icon" viewBox="0 0 24 24" width="$px" height="$px" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">""" +
      s"""<defs>${glow(id)}</defs>${b.out(id)}</svg>"""
  }
}
