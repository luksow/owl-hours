package owlhours.app.logic

/** What the install card offers (spec §10): the browser's own dialog, or iOS's Share → Add to Home Screen. */
enum InstallHint:
  case Prompt
  case IosShare

object InstallHint:
  final case class Env(promptReady: Boolean, ios: Boolean, standalone: Boolean)

  def of(env: Env): Option[InstallHint] =
    if env.standalone then None
    else if env.promptReady then Some(Prompt)
    else if env.ios then Some(IosShare)
    else None

  /** iPadOS Safari reports a Mac user agent; only the touch screen tells it apart. */
  def isIos(userAgent: String, maxTouchPoints: Int): Boolean =
    List("iPhone", "iPad", "iPod").exists(userAgent.contains) || (userAgent.contains("Macintosh") && maxTouchPoints > 1)
