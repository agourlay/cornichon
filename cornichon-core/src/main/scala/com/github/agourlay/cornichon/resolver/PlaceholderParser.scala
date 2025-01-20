package com.github.agourlay.cornichon.resolver

import com.github.agourlay.cornichon.core.{ CornichonError, Session }
import com.github.agourlay.cornichon.resolver.PlaceholderParser._
import org.parboiled2._

import scala.util.{ Failure, Success }

class PlaceholderParser(val input: ParserInput) extends Parser {

  protected def placeholdersRule = rule {
    Ignore ~ zeroOrMore(PlaceholderRule).separatedBy(Ignore) ~ Ignore ~ EOI
  }

  private def PlaceholderRule = rule('<' ~ PlaceholderTXT ~ OptIndex ~ '>' ~> ((k: String, i: Option[Int]) => Placeholder(k, i)))

  private def OptIndex = rule(optional('[' ~ Number ~ ']'))

  private def PlaceholderTXT = rule(capture(oneOrMore(allowedCharsInPlaceholdersPredicate)))

  private def Ignore = rule { zeroOrMore(!PlaceholderRule ~ ANY) }

  private def Number = rule { capture(Digits) ~> (_.toInt) }

  private def Digits = rule { oneOrMore(CharPredicate.Digit) }
}

object PlaceholderParser {
  private val noPlaceholders = Right(Vector.empty)
  private val allowedCharsInPlaceholdersPredicate: CharPredicate = CharPredicate.Visible -- Session.notAllowedInKey

  def parse(input: String): Either[CornichonError, Vector[Placeholder]] =
    if (!input.contains("<"))
      // No need to parse the whole thing
      noPlaceholders
    else {
      val p = new PlaceholderParser(input)
      p.placeholdersRule.run() match {
        case Failure(e: ParseError) =>
          Left(PlaceholderParsingError(input, p.formatError(e, new ErrorFormatter(showTraces = true))))
        case Failure(e: Throwable) =>
          Left(PlaceholderError(input, e))
        case Success(dt) =>
          Right(dt.toVector) // parser produces a vector under the hood
      }
    }
}

case class Placeholder(key: String, index: Option[Int]) {
  val fullKey = index match {
    case Some(i) => s"<$key[$i]>"
    case None    => s"<$key>"
  }
}

case class PlaceholderError(input: String, error: Throwable) extends CornichonError {
  lazy val baseErrorMessage = s"error '${error.getMessage}' thrown during placeholder parsing for input $input"
}

case class PlaceholderParsingError(input: String, error: String) extends CornichonError {
  lazy val baseErrorMessage = s"error '$error' during placeholder parsing for input $input"
}