package com.github.agourlay.cornichon.json

import org.parboiled2._
import scala.util.{ Success, Failure }

class JsonPathParser(val input: ParserInput) extends Parser {

  def placeholdersRule = rule {
    oneOrMore(SegmentRule).separatedBy('.') ~ EOI
  }

  def SegmentRule = rule(Field ~ optIndex ~> JsonSegment)

  val notAllowedInField = "\r\n[]. "

  def optIndex = rule(optional('[' ~ Number ~ ']'))

  def Field = rule(capture(oneOrMore(CharPredicate.Visible -- notAllowedInField)))

  def Number = rule { capture(Digits) ~> (_.toInt) }

  def Digits = rule { oneOrMore(CharPredicate.Digit) }
}

object JsonPathParser {
  def parseJsonPath(input: String): List[JsonSegment] = {
    val p = new JsonPathParser(input)
    p.placeholdersRule.run() match {
      case Failure(e: ParseError) ⇒
        throw new JsonPathParsingError(input, p.formatError(e, new ErrorFormatter(showTraces = true)))
      case Failure(e: Throwable) ⇒
        throw new JsonPathError(input, e)
      case Success(dt) ⇒
        dt.toList
    }
  }
}

case class JsonSegment(field: String, index: Option[Int]) {
  val fullKey = index.fold(s"$field") { index ⇒ s"$field[$index]" }
}
