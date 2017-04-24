package com.github.agourlay.cornichon.matchers

import java.util.regex.Pattern

import com.github.agourlay.cornichon.json.CornichonJson
import io.circe.Json

object MatcherService {

  private val resolver = MatcherResolver()

  // Throws if it finds an unregistered matcher
  def findAllMatchers(input: String): List[Matcher] =
    resolver.findAllMatchers(input).fold(e ⇒ throw e.toException, identity)

  // Removes JSON fields targetted by matchers and builds corresponding matchers assertions
  def prepareMatchers(matchers: List[Matcher], expected: Json, actual: Json): (Json, Json, Seq[MatcherAssertion]) =
    if (matchers.isEmpty)
      (expected, actual, Nil)
    else {
      val pathAssertions = CornichonJson.findAllJsonWithValue(matchers.map(_.fullKey), expected)
        .zip(matchers)
        .map { case (jsonPath, matcher) ⇒ (jsonPath, MatcherAssertion.atJsonPath(jsonPath, actual, matcher)) }

      val jsonPathToIgnore = pathAssertions.map(_._1)
      val newExpected = CornichonJson.removeFieldsByPath(expected, jsonPathToIgnore)
      val newActual = CornichonJson.removeFieldsByPath(actual, jsonPathToIgnore)
      (newExpected, newActual, pathAssertions.map(_._2))
    }

  // Add quotes around known matchers
  def quoteMatchers(input: String) =
    resolver.builtInMatchers.foldLeft(input) {
      case (i, m) ⇒ i.replaceAll(Pattern.quote(m.fullKey), '"' + m.fullKey + '"')
    }
}
