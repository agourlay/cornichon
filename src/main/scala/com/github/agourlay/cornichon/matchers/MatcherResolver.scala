package com.github.agourlay.cornichon.matchers

import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.either._
import com.github.agourlay.cornichon.core.CornichonError
import org.parboiled2.ParseError
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
    //    case "any-uuid"             ⇒ ""
    case "any-integer" ⇒ AnyIntMatcher("<<any-integer>>", (x: String) ⇒ Try(Integer.parseInt(x)).isSuccess)
    case "any-string"  ⇒ AnyStringMatcher("<<>any-string>", (x: String) ⇒ true)
    //    case "any-positive-integer" ⇒ ""
    //    case "any-string"           ⇒ ""
    //    case "any-alphanum-string"  ⇒ ""
    //    case "any-boolean"          ⇒ ""
    //    case "any-date"             ⇒ ""
    //    case "any-time"             ⇒ ""
    //    case "any-date-time"        ⇒ ""
  }

  def findAllMatchers(input: String): Either[CornichonError, List[Matcher]] =
    findMatcherKeys(input).flatMap(_.traverseU(resolveMatcherKeys))
}

trait Matcher {
  def key: String
  def predicate: String ⇒ Boolean
}

case class AnyIntMatcher(key: String, predicate: String ⇒ Boolean) extends Matcher
case class AnyStringMatcher(key: String, predicate: String ⇒ Boolean) extends Matcher

object MatcherResolver {
  def apply(): MatcherResolver = new MatcherResolver()
}

case class MatcherResolverParsingError(input: String, error: Throwable) extends CornichonError {
  val baseErrorMessage = s"error '${error.getMessage}' thrown during matcher parsing for input $input"
}

case class MatcherUndefined(name: String) extends CornichonError {
  val baseErrorMessage = s"error " // Todo
}