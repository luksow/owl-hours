package owlhours.model

import owlhours.domain.BacUnit

enum Impairment:
  case Sober, WarmingUp, Tipsy, Buzzed, Drunk, Wobbly, TakeCare

object Impairment:
  def fromPermille(p: Double): Impairment =
    if p < 0.2 then Sober
    else if p < 0.5 then WarmingUp
    else if p < 0.8 then Tipsy
    else if p < 1.2 then Buzzed
    else if p < 2.0 then Drunk
    else if p <= 3.0 then Wobbly
    else TakeCare

  def fromMgDl(bacMgDl: Double): Impairment = fromPermille(bacMgDl / BacUnit.MgDlPerPermille)
