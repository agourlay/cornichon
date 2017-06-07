package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.core.CornichonError
import org.parboiled2._

import scala.util.{ Failure, Success }
import com.github.agourlay.cornichon.json.JsonPathParser._

class JsonPathParser(val input: ParserInput) extends Parser {

  def placeholdersRule = rule {
    oneOrMore(SegmentRule).separatedBy('.') ~ EOI
  }

  def SegmentRule = rule {
    Field ~ optIndex ~> ((f, i) ⇒ buildSegment(f, i)) |
      '`' ~ FieldWithDot ~ optIndex ~ '`' ~> ((f, i) ⇒ buildSegment(f, i))
  }

  def optIndex = rule(optional('[' ~ (Number | capture('*')) ~ ']'))

  def Field = rule(capture(oneOrMore(CharPredicate.Visible -- notAllowedInField -- '.')))

  def FieldWithDot = rule(capture(oneOrMore(CharPredicate.Visible -- notAllowedInField)))

  def Number = rule { capture(Digits) ~> (_.toInt) }

  def Digits = rule { oneOrMore(CharPredicate.Digit) }

  // The values in the Option are constrainted in the parser itself (any Number or '*')
  private def buildSegment(field: String, index: Option[Any]): JsonPathSegment = index match {
    case Some(i: Int) ⇒ FieldSegment(field, Some(i))
    case Some("*")    ⇒ ArrayProjectionSegment(field)
    case _            ⇒ FieldSegment(field, None)
  }

}

object JsonPathParser {

  val notAllowedInField = "\r\n[]` "

  def parseJsonPath(input: String): Either[CornichonError, List[JsonPathSegment]] = {
    val p = new JsonPathParser(input)
    p.placeholdersRule.run() match {
      case Failure(e: ParseError) ⇒
        Left(JsonPathParsingError(input, p.formatError(e, new ErrorFormatter(showTraces = true))))
      case Failure(e: Throwable) ⇒
        Left(JsonPathError(input, e))
      case Success(dt) ⇒
        Right(dt.toList)
    }
  }
}

trait JsonPathSegment
case class FieldSegment(field: String, index: Option[Int]) extends JsonPathSegment
case class ArrayProjectionSegment(field: String) extends JsonPathSegment
