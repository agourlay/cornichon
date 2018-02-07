package com.github.agourlay.cornichon.matchers

import java.util.regex.Pattern

import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.either._
import cats.syntax.either._
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson
import com.github.agourlay.cornichon.matchers.Matchers._
import io.circe.Json

case class MatcherResolver(matchers: List[Matcher] = Nil) {

  private val allMatchers = MatcherResolver.builtInMatchers ::: matchers
  private val allMatchersByKey = allMatchers.groupBy(_.key)

  def findMatcherKeys(input: String): Either[CornichonError, List[MatcherKey]] =
    MatcherParser.parse(input)

  def resolveMatcherKeys(mk: MatcherKey): Either[CornichonError, Matcher] =
    allMatchersByKey(mk.key) match {
      case Nil         ⇒ Left(MatcherUndefined(mk.key))
      case m :: Nil    ⇒ Right(m)
      case m :: others ⇒ Left(DuplicateMatcherDefinition(m.key, (m :: others).map(_.description)))
    }

  def findAllMatchers(input: String): Either[CornichonError, List[Matcher]] =
    findMatcherKeys(input).flatMap(_.traverse(resolveMatcherKeys))

  // Add quotes around known matchers
  def quoteMatchers(input: String): String =
    allMatchers.foldLeft(input) {
      case (i, m) ⇒ i.replaceAll(Pattern.quote(m.fullKey), '"' + m.fullKey + '"')
    }

  // Removes JSON fields targetted by matchers and builds corresponding matchers assertions
  def prepareMatchers(matchers: List[Matcher], expected: Json, actual: Json, negate: Boolean): (Json, Json, Seq[MatcherAssertion]) =
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

  val builtInMatchers =
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