package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.core.CornichonError
import org.parboiled2._

import scala.util.{ Failure, Success }

class JsonPathParser(val input: ParserInput) extends Parser {

  def placeholdersRule = rule {
    oneOrMore(SegmentRule).separatedBy('.') ~ EOI
  }

  def SegmentRule = rule(('`' ~ FieldWithDot ~ optIndex ~ '`' | Field ~ optIndex) ~> JsonSegment)

  def optIndex = rule(optional('[' ~ Number ~ ']'))

  def Field = rule(capture(oneOrMore(CharPredicate.Visible -- JsonPathParser.notAllowedInField -- '.')))

  def FieldWithDot = rule(capture(oneOrMore(CharPredicate.Visible -- JsonPathParser.notAllowedInField)))

  def Number = rule { capture(Digits) ~> (_.toInt) }

  def Digits = rule { oneOrMore(CharPredicate.Digit) }
}

object JsonPathParser {

  val notAllowedInField = "\r\n[]` "

  def parseJsonPath(input: String): Either[CornichonError, List[JsonSegment]] = {
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

case class JsonSegment(field: String, index: Option[Int]) {
  val fullKey = index.fold(s"$field") { index ⇒ s"$field[$index]" }
}
