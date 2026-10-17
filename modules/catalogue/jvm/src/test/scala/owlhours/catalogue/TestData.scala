package owlhours.catalogue

import scala.io.Source

object TestData {
  lazy val json: String = {
    val src = Source.fromResource("catalogue.json")("UTF-8")
    try src.mkString
    finally src.close()
  }

  lazy val catalogue: Catalogue =
    Catalogue.fromJson(json).fold(e => throw AssertionError("catalogue.json does not load:\n" + e.toList.mkString("\n")), identity)
}
