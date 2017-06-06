package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.core.{ CornichonError, CornichonException }
import org.parboiled2._

import scala.util.{ Failure, Success, Try }

class JsonPathParser(val input: ParserInput) extends Parser {

  def placeholdersRule = rule {
    oneOrMore(SegmentRule).separatedBy('.') ~ EOI
  }

  def SegmentRule = rule(('`' ~ FieldWithDot ~ optIndex ~ '`' | Field ~ optIndex) ~> ((x, i) ⇒ JsonPathSegment.build(x, i)))

  def optIndex = rule(optional('[' ~ Index ~ ']'))

  def Field = rule(capture(oneOrMore(CharPredicate.Visible -- JsonPathParser.notAllowedInField -- '.')))

  def FieldWithDot = rule(capture(oneOrMore(CharPredicate.Visible -- JsonPathParser.notAllowedInField)))

  def Index = rule { capture(oneOrMore(CharPredicate.Visible -- JsonPathParser.notAllowedInField)) }

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

object JsonPathSegment {
  def build(field: String, index: Option[String]): JsonPathSegment =
    index.fold[JsonPathSegment](JsonFieldSegment(field)) { indice ⇒
      if (indice == "*")
        JsonArrayProjectionSegment(field)
      else
        Try { Integer.parseInt(indice) } match {
          case Success(i) ⇒ JsonArrayIndiceSegment(field, i)
          case Failure(e) ⇒ throw CornichonException(e.getMessage)
        }
    }
}

case class JsonFieldSegment(field: String) extends JsonPathSegment
case class JsonArrayIndiceSegment(field: String, index: Int) extends JsonPathSegment
case class JsonArrayProjectionSegment(field: String) extends JsonPathSegment
