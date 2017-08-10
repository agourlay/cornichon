package com.github.agourlay.cornichon.matchers

import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.either._
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.matchers.Matchers._

class MatcherResolver() {

  //Could be extended with custom matchers from users
  val matchers = MatcherResolver.builtInMatchers

  def findMatcherKeys(input: String): Either[CornichonError, List[MatcherKey]] =
    MatcherParser.parse(input)

  def resolveMatcherKeys(m: MatcherKey): Either[CornichonError, Matcher] =
    matchers.find(_.key == m.key).map(Right(_)).getOrElse(Left(MatcherUndefined(m.key)))

  def findAllMatchers(input: String): Either[CornichonError, List[Matcher]] =
    findMatcherKeys(input).flatMap(_.traverseU(resolveMatcherKeys))
}

object MatcherResolver {
  def apply(): MatcherResolver = new MatcherResolver()

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
  val baseErrorMessage = s"there is no matcher named '$name' defined."
}