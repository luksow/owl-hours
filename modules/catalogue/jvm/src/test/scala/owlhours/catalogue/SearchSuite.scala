package owlhours.catalogue

import owlhours.domain.CatalogueId

class SearchSuite extends munit.FunSuite:
  private def ids(q: String, boost: CatalogueId => Int = _ => 0) =
    Search.search(q, TestData.catalogue.all, boost).map(_.id.unwrap)

  test("exact name beats prefix: 'aperol' finds Aperol before Aperol Spritz") {
    assertEquals(ids("aperol").take(2), List("aperol", "aperol-spritz"))
  }

  test("aliases and ASCII input find accented and abbreviated drinks") {
    assertEquals(ids("g&t").head, "gin-tonic")
    assertEquals(ids("zubrowka").head, "zubrowka")
    assertEquals(ids("pils").head, "pale-lager")
    assertEquals(ids("dark and stormy").head, "dark-n-stormy")
    assert(ids("jager").take(2).toSet == Set("jagermeister", "jagerbomb"))
  }

  test("prefix and word matching") {
    assertEquals(ids("neg").head, "negroni")
    assert(ids("russian").toSet.contains("white-russian"))
    assert(ids("russian").toSet.contains("black-russian"))
  }

  test("one typo is forgiven") {
    assertEquals(ids("negorni").head, "negroni")
    assertEquals(ids("margarira").head, "margarita")
  }

  test("empty query lists everything, boosted entries first") {
    val boosted = ids("", id => if id.unwrap == "mojito" then 10 else 0)
    assertEquals(boosted.head, "mojito")
    assertEquals(boosted.size, 30) // default limit
  }

  test("no match returns nothing") {
    assertEquals(ids("xyzzy"), Nil)
  }
