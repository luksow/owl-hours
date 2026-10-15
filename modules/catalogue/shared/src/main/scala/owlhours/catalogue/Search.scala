package owlhours.catalogue

import owlhours.domain.CatalogueId

object Search:
  /** Scala.js has no java.text.Normalizer, so diacritics are folded with an explicit table. */
  private val folds: Map[Char, String] = Map(
    'à' -> "a", 'á' -> "a", 'â' -> "a", 'ã' -> "a", 'ä' -> "a", 'å' -> "a", 'ą' -> "a", 'æ' -> "ae",
    'ç' -> "c", 'ć' -> "c", 'č' -> "c", 'ď' -> "d",
    'è' -> "e", 'é' -> "e", 'ê' -> "e", 'ë' -> "e", 'ę' -> "e", 'ě' -> "e",
    'ì' -> "i", 'í' -> "i", 'î' -> "i", 'ï' -> "i", 'ł' -> "l",
    'ñ' -> "n", 'ń' -> "n", 'ň' -> "n",
    'ò' -> "o", 'ó' -> "o", 'ô' -> "o", 'õ' -> "o", 'ö' -> "o", 'ø' -> "o", 'œ' -> "oe",
    'ř' -> "r", 'ś' -> "s", 'š' -> "s", 'ß' -> "ss", 'ť' -> "t",
    'ù' -> "u", 'ú' -> "u", 'û' -> "u", 'ü' -> "u", 'ů' -> "u",
    'ý' -> "y", 'ÿ' -> "y", 'ź' -> "z", 'ż' -> "z", 'ž' -> "z"
  )

  def normalize(s: String): String =
    val folded = s.toLowerCase.flatMap {
      case '&'                               => " and "
      case c if folds.contains(c)            => folds(c)
      case c if c.isLetterOrDigit && c < 128 => c.toString
      case _                                 => " "
    }
    folded.split(' ').filter(_.nonEmpty).mkString(" ")

  def search(query: String, entries: Seq[Entry], boost: CatalogueId => Int = _ => 0, limit: Int = 30): List[Entry] =
    val q = normalize(query)
    val ranked =
      if q.isEmpty then entries.map(e => (e, 0))
      else entries.map(e => (e, score(q, e))).filter(_._2 > 0)
    ranked.sortBy((e, s) => (-s, -boost(e.id), e.name)).take(limit).map(_._1).toList

  private def score(q: String, e: Entry): Int =
    val qWords = q.split(' ').toList
    val name   = normalize(e.name)
    val als    = e.aliases.map(normalize)
    def wordsOf(c: String)     = c.split(' ').toList
    def prefixWords(c: String) = qWords.forall(w => wordsOf(c).exists(_.startsWith(w)))
    def nearWords(c: String) =
      qWords.forall(w => w.length >= 4 && wordsOf(c).exists(x => x.startsWith(w) || osa(w, x) <= 1))
    val candidates = name :: als
    List(
      if name == q then 100 else 0,
      if als.contains(q) then 90 else 0,
      if name.startsWith(q) then 80 else 0,
      if als.exists(_.startsWith(q)) then 70 else 0,
      if candidates.exists(prefixWords) then 60 else 0,
      if candidates.exists(_.contains(q)) then 40 else 0,
      if candidates.exists(nearWords) then 25 else 0
    ).max

  /** Optimal string alignment distance (Levenshtein + adjacent transposition). */
  private def osa(a: String, b: String): Int =
    val d = Array.ofDim[Int](a.length + 1, b.length + 1)
    for i <- 0 to a.length do d(i)(0) = i
    for j <- 0 to b.length do d(0)(j) = j
    for i <- 1 to a.length; j <- 1 to b.length do
      val cost = if a(i - 1) == b(j - 1) then 0 else 1
      d(i)(j) = math.min(math.min(d(i - 1)(j) + 1, d(i)(j - 1) + 1), d(i - 1)(j - 1) + cost)
      if i > 1 && j > 1 && a(i - 1) == b(j - 2) && a(i - 2) == b(j - 1) then
        d(i)(j) = math.min(d(i)(j), d(i - 2)(j - 2) + 1)
    d(a.length)(b.length)
