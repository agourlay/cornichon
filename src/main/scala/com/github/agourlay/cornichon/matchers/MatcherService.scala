package com.github.agourlay.cornichon.matchers

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.{ CornichonJson, JsonPath }
import io.circe.Json
import cats.syntax.either._

object MatcherService {

  private val resolver = MatcherResolver()

  private def resolveAllMatchers(json: Json): Either[CornichonError, List[((String, Json), Matcher)]] =
    resolver.findAllMatchers(json.noSpaces).map { matchers ⇒
      CornichonJson.findAllJsonWithValue(matchers.map(_.fullKey), json).zip(matchers)
    }

  private def buildJsonMatcherAssertions(in: List[((String, Json), Matcher)], expectedJson: Json): List[(JsonPath, MatcherAssertion)] =
    in.map {
      case ((path, _), matcher) ⇒
        val jsonPath = JsonPath.parse(path)
        (jsonPath, MatcherAssertion.atJsonPath(jsonPath, expectedJson, matcher))
    }

  def prepareMatchers(expectedJson: Json, input: Json): (Json, Json, Seq[MatcherAssertion]) = {
    val pathAssertions = resolveAllMatchers(expectedJson).map(buildJsonMatcherAssertions(_, input)).fold(e ⇒ throw e, identity)

    val jsonPathToIgnore = pathAssertions.map(_._1)
    val newExpected = CornichonJson.removeFieldsByPath(expectedJson, jsonPathToIgnore)
    val newInput = CornichonJson.removeFieldsByPath(input, jsonPathToIgnore)

    (newExpected, newInput, pathAssertions.map(_._2))
  }
}
