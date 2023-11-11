package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.core.CornichonError
import org.parboiled2._

import scala.util.{ Failure, Success }
import com.github.agourlay.cornichon.json.JsonPathParser._

class JsonPathParser(val input: ParserInput) extends Parser {

  protected def placeholdersRule = rule {
    oneOrMore(SegmentRule).separatedBy('.') ~ EOI
  }

  private def SegmentRule = rule {
    Field ~ OptIndex ~> ((f, i) => toOp(f, i)) |
      '`' ~ FieldWithDot ~ OptIndex ~ '`' ~> ((f, i) => toOp(f, i))
  }

  private def OptIndex = rule(optional('[' ~ (Number | capture('*')) ~ ']'))

  private def Field = rule(capture(oneOrMore(allowedInFieldPredicate)))

  private def FieldWithDot = rule(capture(oneOrMore(allowedInFieldWithDotPredicate)))

  private def Number = rule { capture(Digits) ~> (_.toInt) }

  private def Digits = rule { oneOrMore(CharPredicate.Digit) }

  // The value in the Option is constrained in the parser itself (any Number or '*')
  private def toOp(field: String, index: Option[Any]): JsonPathOperation =
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

  def parseJsonPath(input: String): Either[CornichonError, Vector[JsonPathOperation]] = {
    val p = new JsonPathParser(input)
    p.placeholdersRule.run() match {
      case Failure(e: ParseError) =>
        Left(JsonPathParsingError(input, p.formatError(e, new ErrorFormatter(showTraces = true))))
      case Failure(e: Throwable) =>
        Left(JsonPathError(input, e))
      case Success(dt) =>
        Right(dt.toVector) // parser produces a vector under the hood
    }
  }
}
