package owlhours.app.logic

import InstallHint.*

class InstallHintSuite extends munit.FunSuite:
  test("the browser's own install dialog wins when it is on offer") {
    assertEquals(of(Env(promptReady = true, ios = false, standalone = false)), Some(Prompt))
  }

  test("iOS gets the Share → Add to Home Screen hint") {
    assertEquals(of(Env(promptReady = false, ios = true, standalone = false)), Some(IosShare))
  }

  test("nothing is offered inside the installed app, on any platform") {
    for ready <- List(true, false); ios <- List(true, false) do
      assertEquals(of(Env(ready, ios, standalone = true)), None)
  }

  test("a browser that offers neither shows nothing") {
    assertEquals(of(Env(promptReady = false, ios = false, standalone = false)), None)
  }

  test("iPhone, iPad and iPadOS (a 'Macintosh' with a touch screen) count as iOS; a Mac and Android do not") {
    val iphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1"
    val ipados = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Safari/605.1.15"
    val android = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0 Mobile Safari/537.36"
    assert(isIos(iphone, 5))
    assert(isIos(ipados, 5))
    assert(!isIos(ipados, 0)) // a Mac
    assert(!isIos(android, 5))
  }
