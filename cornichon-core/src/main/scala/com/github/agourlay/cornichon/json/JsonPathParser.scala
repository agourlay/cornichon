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
    Field ~ optIndex ~> ((f, i) => operation(f, i)) |
      '`' ~ FieldWithDot ~ optIndex ~ '`' ~> ((f, i) => operation(f, i))
  }

  def optIndex = rule(optional('[' ~ (Number | capture('*')) ~ ']'))

  def Field = rule(capture(oneOrMore(allowedInFieldPredicate)))

  def FieldWithDot = rule(capture(oneOrMore(allowedInFieldWithDotPredicate)))

  def Number = rule { capture(Digits) ~> (_.toInt) }

  def Digits = rule { oneOrMore(CharPredicate.Digit) }

  // The value in the Option is constrained in the parser itself (any Number or '*')
  private def operation(field: String, index: Option[Any]): JsonPathOperation =
    (field, index) match {
      case (JsonPath.root, None)         => RootSelection
      case (JsonPath.root, Some(i: Int)) => RootArrayElementSelection(i)
      case (JsonPath.root, _)            => RootArrayFieldProjection
      case (f, None)                     => FieldSelection(f)
      case (f, Some(i: Int))             => ArrayFieldSelection(f, i)
      case (f, _)                        => ArrayFieldProjection(f)
    }

}

object JsonPathParser {

  val notAllowedInField = "\r\n[]` "

  private val allowedInFieldWithDotPredicate: CharPredicate = CharPredicate.Visible -- notAllowedInField
  private val allowedInFieldPredicate = allowedInFieldWithDotPredicate -- '.'

  def parseJsonPath(input: String): Either[CornichonError, List[JsonPathOperation]] = {
    val p = new JsonPathParser(input)
    p.placeholdersRule.run() match {
      case Failure(e: ParseError) =>
        Left(JsonPathParsingError(input, p.formatError(e, new ErrorFormatter(showTraces = true))))
      case Failure(e: Throwable) =>
        Left(JsonPathError(input, e))
      case Success(dt) =>
        Right(dt.toList)
    }
  }
}
