package owlhours.model

object Solver:
  trait Rhs:
    def apply(t: Double, y: Array[Double], dy: Array[Double]): Unit
