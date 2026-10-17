package owlhours.app.logic

import owlhours.catalogue.*
import owlhours.domain.*

/** Default drinking time by serve type (spec §6.3): shot 1 min, cocktail 15, wine glass 20, pint 30. */
object Durations {
  def default(entry: Entry, volume: Millilitres): Minutes = Minutes(entry match {
    case _: Recipe => 15
    case s: Style =>
      s.category match {
        case Category.Beer | Category.Cider | Category.Seltzer => if volume.unwrap >= 400 then 30 else 20
        case Category.Fortified                                => 10
        case _                                                 => 20
      }
    case p: Product =>
      p.category match {
        case Category.Spirit | Category.Liqueur | Category.Bitters => if volume.unwrap <= 60 then 1 else 10
        case Category.Aperitif | Category.Vermouth                 => 10
        case _                                                     => 5
      }
  }
  )
}
