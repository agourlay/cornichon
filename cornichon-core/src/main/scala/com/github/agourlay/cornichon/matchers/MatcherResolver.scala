package com.github.agourlay.cornichon.matchers

import cats.syntax.either._
import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.either._
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson
import com.github.agourlay.cornichon.matchers.Matchers._
import com.github.agourlay.cornichon.util.Caching
import io.circe.Json

object MatcherResolver {

  private val matchersCache = Caching.buildCache[String, Either[CornichonError, List[MatcherKey]]]()

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

  def findMatcherKeys(input: String): Either[CornichonError, List[MatcherKey]] =
    MatcherParser.parse(input)

  def resolveMatcherKeys(allMatchers: Map[String, List[Matcher]])(mk: MatcherKey): Either[CornichonError, Matcher] =
    allMatchers.get(mk.key) match {
      case None              => MatcherUndefined(mk.key).asLeft
      case Some(Nil)         => MatcherUndefined(mk.key).asLeft
      case Some(m :: Nil)    => m.asRight
      case Some(m :: others) => DuplicateMatcherDefinition(m.key, (m :: others).map(_.description)).asLeft
    }

  def findAllMatchers(allMatchers: Map[String, List[Matcher]])(input: String): Either[CornichonError, List[Matcher]] =
    matchersCache.get(input, i => findMatcherKeys(i)).flatMap(_.traverse(resolveMatcherKeys(allMatchers)))

  // Add quotes around known matchers
  def quoteMatchers(input: String, matchersToQuote: List[Matcher]): String =
    matchersToQuote.distinct.foldLeft(input) {
      case (i, m) => m.pattern.matcher(i).replaceAll(m.quotedFullKey)
    }

  // Removes JSON fields targeted by matchers and builds corresponding matchers assertions
  def prepareMatchers(matchers: List[Matcher], expected: Json, actual: Json, negate: Boolean): (Json, Json, List[MatcherAssertion]) =
    if (matchers.isEmpty)
      (expected, actual, Nil)
    else {
      val pathAssertions = CornichonJson.findAllPathWithValue(matchers.map(_.fullKey), expected)
        .iterator
        .zip(matchers)
        .map { case (jsonPath, matcher) => MatcherAssertion(negate, matcher, actual, jsonPath) }
        .toList

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
    s"${descriptions.map(d => s"'$d'").mkString(" and ")}"
}