package com.github.agourlay.cornichon.matchers

import java.util.UUID

import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.either._
import cats.syntax.either._
import com.github.agourlay.cornichon.core.CornichonError
import org.parboiled2.ParseError
import com.github.agourlay.cornichon.matchers.Matchers._

import scala.util.{ Failure, Success, Try }

class MatcherResolver() {

  def findMatcherKeys(input: String): Either[CornichonError, List[MatcherKey]] = {
    new MatcherParser(input).matchersRule.run() match {
      case Failure(e: ParseError) ⇒ Right(List.empty)
      case Failure(e: Throwable)  ⇒ Left(MatcherResolverParsingError(input, e))
      case Success(dt)            ⇒ Right(dt.toList)
    }
  }

  def resolveMatcherKeys(m: MatcherKey): Either[CornichonError, Matcher] =
    builtInMatchers.lift(m.key).map(Right(_)).getOrElse(Left(MatcherUndefined(m.key)))

  def builtInMatchers: PartialFunction[String, Matcher] = {
    case isPresentMatcher.key      ⇒ isPresentMatcher
    case anyIntMatcher.key         ⇒ anyIntMatcher
    case anyNegativeIntMatcher.key ⇒ anyNegativeIntMatcher
    case anyPositiveIntMatcher.key ⇒ anyPositiveIntMatcher
    case anyUUIDMatcher.key        ⇒ anyUUIDMatcher
    case anyBooleanMatcher.key     ⇒ anyBooleanMatcher
    //    case "any-alphanum-string"  ⇒ ""
    //    case "any-date"             ⇒ ""
    //    case "any-time"             ⇒ ""
    //    case "any-date-time"        ⇒ ""
  }

  def findAllMatchers(input: String): Either[CornichonError, List[Matcher]] =
    findMatcherKeys(input).flatMap(_.traverseU(resolveMatcherKeys))
}

case class Matcher(key: String, predicate: String ⇒ Boolean) {
  val fullKey = s"<<$key>>"
}

object Matchers {
  val isPresentMatcher = Matcher("is-present", x ⇒ x.nonEmpty)
  val anyIntMatcher = Matcher("any-integer", x ⇒ Try(Integer.parseInt(x)).isSuccess)
  val anyPositiveIntMatcher = Matcher("any-positive-integer", x ⇒ Integer.parseInt(x) > 0)
  val anyNegativeIntMatcher = Matcher("any-negative-integer", x ⇒ Integer.parseInt(x) < 0)
  val anyUUIDMatcher = Matcher("any-uuid", x ⇒ Try(UUID.fromString(x)).isSuccess)
  val anyBooleanMatcher = Matcher("any-boolean", x ⇒ x == "true" || x == "false")
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