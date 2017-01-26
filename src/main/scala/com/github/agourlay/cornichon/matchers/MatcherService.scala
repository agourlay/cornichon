package com.github.agourlay.cornichon.matchers

import com.github.agourlay.cornichon.json.CornichonJson
import io.circe.Json
import cats.syntax.either._

object MatcherService {

  private val resolver = MatcherResolver()

  def prepareMatchers(expected: Json, actual: Json): (Json, Json, Seq[MatcherAssertion]) =
    resolver.findAllMatchers(expected.noSpaces).map { matchers ⇒
      if (matchers.isEmpty)
        (expected, actual, Nil)
      else {
        val pathAssertions = CornichonJson.findAllJsonWithValue(matchers.map(_.fullKey), expected)
          .zip(matchers)
          .map {
            case (jsonPath, matcher) ⇒ (jsonPath, MatcherAssertion.atJsonPath(jsonPath, actual, matcher))
          }

        val jsonPathToIgnore = pathAssertions.map(_._1)
        val newExpected = CornichonJson.removeFieldsByPath(expected, jsonPathToIgnore)
        val newActual = CornichonJson.removeFieldsByPath(actual, jsonPathToIgnore)
        (newExpected, newActual, pathAssertions.map(_._2))
      }
    }.fold(e ⇒ throw e, identity)
}
