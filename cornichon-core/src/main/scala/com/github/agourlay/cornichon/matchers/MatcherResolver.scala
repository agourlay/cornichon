package com.github.agourlay.cornichon.matchers

import cats.syntax.either._
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson
import com.github.agourlay.cornichon.matchers.Matchers._
import com.github.agourlay.cornichon.util.TraverseUtils.traverse
import com.github.agourlay.cornichon.util.StringUtils._
import io.circe.Json

object MatcherResolver {

  val builtInMatchers: List[Matcher] =
    isPresent ::
      isNull ::
      anyString ::
      anyArray ::
      anyObject ::
      anyInteger ::
      anyPositiveInteger ::
      anyNegativeInteger ::
      anyUUID ::
      anyBoolean ::
      anyAlphaNum ::
      anyDate ::
      anyDateTime ::
      anyTime ::
      Nil

  def findMatcherKeys(input: String): Either[CornichonError, Vector[MatcherKey]] =
    MatcherParser.parse(input)

  def resolveMatcherKeys(allMatchers: Map[String, List[Matcher]])(mk: MatcherKey): Either[CornichonError, Matcher] =
    allMatchers.get(mk.key) match {
      case None              => MatcherUndefined(mk.key).asLeft
      case Some(Nil)         => MatcherUndefined(mk.key).asLeft
      case Some(m :: Nil)    => m.asRight
      case Some(m :: others) => DuplicateMatcherDefinition(m.key, (m :: others).map(_.description)).asLeft
    }

  def findAllMatchers(allMatchers: Map[String, List[Matcher]])(input: String): Either[CornichonError, Vector[Matcher]] =
    findMatcherKeys(input).flatMap { traverse(_)(resolveMatcherKeys(allMatchers)) }

  // Add quotes around known matchers
  def quoteMatchers(input: String, matchersToQuote: Vector[Matcher]): String = {
    val pairs = matchersToQuote.map(m => (m.fullKey, m.quotedFullKey))
    replacePatternsInOrder(input, pairs)
  }

  // Removes JSON fields targeted by matchers and builds corresponding matchers assertions
  def prepareMatchers(matchers: Vector[Matcher], expected: Json, actual: Json, negate: Boolean): (Json, Json, List[MatcherAssertion]) =
    if (matchers.isEmpty)
      (expected, actual, Nil)
    else {
      val matcherKeys = matchers.iterator.map(_.fullKey).toSet
      val pathAssertions = CornichonJson.findAllPathWithValue(matcherKeys, expected)
        .map {
          case (matcherKey, jsonPath) =>
            // find corresponding matcher for the tuple
            val matcher = matchers.find(_.fullKey == matcherKey) match {
              case Some(value) => value
              case None        => throw new IllegalStateException(s"Matchers $matchers must contain an entry for $matcherKey")
            }
            MatcherAssertion(negate, matcher, actual, jsonPath)
        }

      val jsonPathToIgnore = pathAssertions.map(_.jsonPath)
      val newExpected = CornichonJson.removeFieldsByPath(expected, jsonPathToIgnore)
      val newActual = CornichonJson.removeFieldsByPath(actual, jsonPathToIgnore)
      (newExpected, newActual, pathAssertions)
    }
}

case class MatcherUndefined(name: String) extends CornichonError {
  lazy val baseErrorMessage = s"there is no matcher named '$name' defined."
}

case class DuplicateMatcherDefinition(name: String, descriptions: List[String]) extends CornichonError {
  lazy val baseErrorMessage = s"there are ${descriptions.size} matchers named '$name': " +
    s"${descriptions.iterator.map(d => s"'$d'").mkString(" and ")}"
}