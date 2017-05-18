package com.github.agourlay.cornichon.resolver

import com.github.agourlay.cornichon.core.{ CornichonError, Session }
import org.parboiled2._

import scala.util.{ Failure, Success }

class PlaceholderParser(val input: ParserInput) extends Parser {

  def placeholdersRule = rule {
    Ignore ~ zeroOrMore(PlaceholderRule).separatedBy(Ignore) ~ Ignore ~ EOI
  }

  def PlaceholderRule = rule('<' ~ PlaceholderTXT ~ optIndex ~ '>' ~> Placeholder)

  def optIndex = rule(optional('[' ~ Number ~ ']'))

  def PlaceholderTXT = rule(capture(oneOrMore(CharPredicate.Visible -- Session.notAllowedInKey)))

  def Ignore = rule { zeroOrMore(!PlaceholderRule ~ ANY) }

  def Number = rule { capture(Digits) ~> (_.toInt) }

  def Digits = rule { oneOrMore(CharPredicate.Digit) }
}

object PlaceholderParser {

  def parse(input: String): Either[CornichonError, List[Placeholder]] = {
    val p = new PlaceholderParser(input)
    p.placeholdersRule.run() match {
      case Failure(e: ParseError) ⇒
        Left(PlaceholderParsingError(input, p.formatError(e, new ErrorFormatter(showTraces = true))))
      case Failure(e: Throwable) ⇒
        Left(PlaceholderError(input, e))
      case Success(dt) ⇒
        Right(dt.toList)
    }
  }
}

case class Placeholder(key: String, index: Option[Int]) {
  val fullKey = index.fold(s"<$key>") { index ⇒ s"<$key[$index]>" }
}

case class PlaceholderError(input: String, error: Throwable) extends CornichonError {
  val baseErrorMessage = s"error '${error.getMessage}' thrown during placeholder parsing for input $input"
}

case class PlaceholderParsingError(input: String, error: String) extends CornichonError {
  val baseErrorMessage = s"error '$error' during placeholder parsing for input $input"
}