package com.github.agourlay.cornichon.matchers

import com.github.agourlay.cornichon.core.CornichonError
import org.parboiled2.ParseError
import scala.util.{ Failure, Success }

class MatcherResolver() {

  def findMatchers(input: String): Either[CornichonError, List[Matcher]] = {
    new MatcherParser(input).matchersRule.run() match {
      case Failure(e: ParseError) ⇒ Right(List.empty)
      case Failure(e: Throwable)  ⇒ Left(MatcherResolverParsingError(input, e))
      case Success(dt)            ⇒ Right(dt.toList)
    }
  }
}

object MatcherResolver {
  def apply(): MatcherResolver = new MatcherResolver()
}

case class MatcherResolverParsingError(input: String, error: Throwable) extends CornichonError {
  val baseErrorMessage = s"error '${error.getMessage}' thrown during matcher parsing for input $input"
}