package com.github.agourlay.cornichon.matchers

import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.either._
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson
import com.github.agourlay.cornichon.matchers.Matchers._
import com.github.agourlay.cornichon.util.Caching
import io.circe.Json

case class MatcherResolver(matchers: List[Matcher] = Nil) {

  private val allMatchers = MatcherResolver.builtInMatchers ::: matchers
  private val allMatchersByKey = allMatchers.groupBy(_.key)

  // When steps are nested (repeat, eventually, retryMax) it is wasteful to repeat the parsing process of looking for matchers.
  // There is one resolver per Feature so the cache is not living too long.
  private val matchersCache = Caching.buildCache[String, Either[CornichonError, List[Matcher]]]()

  def findMatcherKeys(input: String): Either[CornichonError, List[MatcherKey]] =
    MatcherParser.parse(input)

  def resolveMatcherKeys(mk: MatcherKey): Either[CornichonError, Matcher] =
    allMatchersByKey.get(mk.key) match {
      case None              ⇒ Left(MatcherUndefined(mk.key))
      case Some(Nil)         ⇒ Left(MatcherUndefined(mk.key))
      case Some(m :: Nil)    ⇒ Right(m)
      case Some(m :: others) ⇒ Left(DuplicateMatcherDefinition(m.key, (m :: others).map(_.description)))
    }

  def findAllMatchers(input: String): Either[CornichonError, List[Matcher]] =
    matchersCache.get(input, i ⇒ findMatcherKeys(i).flatMap(_.traverse(resolveMatcherKeys)))

  // Add quotes around known matchers
  def quoteMatchers(input: String, matchersToQuote: List[Matcher]): String =
    matchersToQuote.distinct.foldLeft(input) {
      case (i, m) ⇒ m.pattern.matcher(i).replaceAll(m.quotedFullKey)
    }

  // Removes JSON fields targeted by matchers and builds corresponding matchers assertions
  def prepareMatchers(matchers: List[Matcher], expected: Json, actual: Json, negate: Boolean): (Json, Json, List[MatcherAssertion]) =
    if (matchers.isEmpty)
      (expected, actual, Nil)
    else {
      val pathAssertions = CornichonJson.findAllJsonWithValue(matchers.map(_.fullKey), expected)
        .zip(matchers)
        .map { case (jsonPath, matcher) ⇒ (jsonPath, MatcherAssertion.atJsonPath(jsonPath, actual, matcher, negate)) }

      val jsonPathToIgnore = pathAssertions.map(_._1)
      val newExpected = CornichonJson.removeFieldsByPath(expected, jsonPathToIgnore)
      val newActual = CornichonJson.removeFieldsByPath(actual, jsonPathToIgnore)
      (newExpected, newActual, pathAssertions.map(_._2))
    }
}

object MatcherResolver {

  val builtInMatchers: List[Matcher] =
    isPresent ::
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
}

case class MatcherUndefined(name: String) extends CornichonError {
  lazy val baseErrorMessage = s"there is no matcher named '$name' defined."
}

case class DuplicateMatcherDefinition(name: String, descriptions: List[String]) extends CornichonError {
  lazy val baseErrorMessage = s"there are ${descriptions.size} matchers named '$name': " +
    s"${descriptions.map(d ⇒ s"'$d'").mkString(" and ")}"
}