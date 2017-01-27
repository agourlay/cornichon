package com.github.agourlay.cornichon.matchers

import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.either._
import cats.syntax.either._
import com.github.agourlay.cornichon.core.CornichonError
import org.parboiled2.ParseError
import com.github.agourlay.cornichon.matchers.Matchers._

import scala.util.{ Failure, Success }

class MatcherResolver() {

  val builtInMatchers =
    isPresent ::
      anyArray ::
      anyObject ::
      anyInteger ::
      anyPositiveInteger ::
      anyNegativeIntger ::
      anyUUID ::
      anyBoolean ::
      anyAlphaNum ::
      anyDate ::
      anyDateTime ::
      anyTime ::
      Nil

  def findMatcherKeys(input: String): Either[CornichonError, List[MatcherKey]] = {
    new MatcherParser(input).matchersRule.run() match {
      case Failure(e: ParseError) ⇒ Right(List.empty)
      case Failure(e: Throwable)  ⇒ Left(MatcherResolverParsingError(input, e))
      case Success(dt)            ⇒ Right(dt.toList)
    }
  }

  def resolveMatcherKeys(m: MatcherKey): Either[CornichonError, Matcher] =
    builtInMatchers.find(_.key == m.key).map(Right(_)).getOrElse(Left(MatcherUndefined(m.key)))

  def findAllMatchers(input: String): Either[CornichonError, List[Matcher]] =
    findMatcherKeys(input).flatMap(_.traverseU(resolveMatcherKeys))
}

object MatcherResolver {
  def apply(): MatcherResolver = new MatcherResolver()
}

case class MatcherResolverParsingError(input: String, error: Throwable) extends CornichonError {
  val baseErrorMessage = s"error '${error.getMessage}' thrown during matcher parsing for input $input"
}

case class MatcherUndefined(name: String) extends CornichonError {
  val baseErrorMessage = s"there is no matcher named '$name' defined."
}