package com.github.agourlay.cornichon.matchers

import com.github.agourlay.cornichon.core.CornichonError
import org.parboiled2._

import scala.util.{ Failure, Success }

class MatcherParser(val input: ParserInput) extends Parser {

  def matchersRule = rule {
    Ignore ~ zeroOrMore(MatcherRule).separatedBy(Ignore) ~ Ignore ~ EOI
  }

  def MatcherRule = rule("*" ~ MatcherTXT ~ "*" ~> MatcherKey)

  def MatcherTXT = rule(capture(oneOrMore(CharPredicate.Visible -- MatcherParser.notAllowedInMatchers)))

  def Ignore = rule { zeroOrMore(!MatcherRule ~ ANY) }

  def Number = rule { capture(Digits) ~> (_.toInt) }

  def Digits = rule { oneOrMore(CharPredicate.Digit) }
}

object MatcherParser {
  val notAllowedInMatchers = "\r\n<>* "
  private val noMatchers = Right(Nil)

  def parse(input: String): Either[CornichonError, List[MatcherKey]] =
    if (!input.contains("*"))
      // No need to parse the whole thing
      noMatchers
    else {
      val p = new MatcherParser(input)
      p.matchersRule.run() match {
        case Failure(e: ParseError) ⇒
          Left(MatcherParsingError(input, p.formatError(e, new ErrorFormatter(showTraces = true))))
        case Failure(e: Throwable) ⇒
          Left(MatcherError(input, e))
        case Success(dt) ⇒
          Right(dt.toList)
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