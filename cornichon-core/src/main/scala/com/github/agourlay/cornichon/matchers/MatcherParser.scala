package com.github.agourlay.cornichon.matchers

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.matchers.MatcherParser._
import org.parboiled2._

import scala.util.{ Failure, Success }

class MatcherParser(val input: ParserInput) extends Parser {

  def matchersRule = rule {
    Ignore ~ zeroOrMore(MatcherRule).separatedBy(Ignore) ~ Ignore ~ EOI
  }

  private def MatcherRule = rule("*" ~ MatcherTXT ~ "*" ~> MatcherKey)

  private def MatcherTXT = rule(capture(oneOrMore(allowedCharsInMatcher)))

  private def Ignore = rule { zeroOrMore(!MatcherRule ~ ANY) }
}

object MatcherParser {
  private val notAllowedInMatchers = "\r\n<>* "
  private val noMatchers = Right(Vector.empty)
  private val allowedCharsInMatcher: CharPredicate = CharPredicate.Visible -- MatcherParser.notAllowedInMatchers

  def parse(input: String): Either[CornichonError, Vector[MatcherKey]] =
    if (!input.contains("*"))
      // No need to parse the whole thing
      noMatchers
    else {
      val p = new MatcherParser(input)
      p.matchersRule.run() match {
        case Failure(e: ParseError) =>
          Left(MatcherParsingError(input, p.formatError(e, new ErrorFormatter(showTraces = true))))
        case Failure(e: Throwable) =>
          Left(MatcherError(input, e))
        case Success(dt) =>
          Right(dt.toVector) // parser produces a vector under the hood
      }
    }
}

case class MatcherError(input: String, error: Throwable) extends CornichonError {
  lazy val baseErrorMessage = s"error '${error.getMessage}' thrown during matcher parsing for input $input"
}

case class MatcherParsingError(input: String, error: String) extends CornichonError {
  lazy val baseErrorMessage = s"error '$error' thrown during matcher parsing for input $input"
}

case class MatcherKey(key: String) extends AnyVal