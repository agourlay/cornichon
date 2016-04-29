package com.github.agourlay.cornichon.dsl

import io.circe.{ Json, JsonObject }
import org.parboiled2._

import scala.util.{ Failure, Success }

object DataTableParser {
  def parseDataTable(input: String) = {
    val p = new DataTableParser(input)
    p.dataTableRule.run() match {
      case Failure(e: ParseError) ⇒
        throw new DataTableParseError(p.formatError(e, new ErrorFormatter(showTraces = true)))
      case Failure(e: Throwable) ⇒
        throw new DataTableError(e, input)
      case Success(dt) ⇒ dt
    }
  }
}

class DataTableParser(val input: ParserInput) extends Parser {

  val delimeter = '|'

  def dataTableRule = rule {
    optional(NL) ~ HeaderRule ~ NL ~ oneOrMore(RowRule).separatedBy(NL) ~ optional(NL) ~ EOI ~> DataTable
  }

  def HeaderRule = rule { Separator ~ oneOrMore(HeaderTXT).separatedBy(Separator) ~ Separator ~> Headers }

  //fix me - should not swallow error
  def RowRule = rule { Separator ~ oneOrMore(TXT).separatedBy(Separator) ~ Separator ~> (x ⇒ Row(x.map(io.circe.parser.parse(_).getOrElse(Json.Null)))) }

  val delims = s"$delimeter\r\n"

  def HeaderTXT = rule(capture(oneOrMore(CharPredicate.Visible -- delims)))

  def TXT = rule(capture(oneOrMore(CharPredicate.Printable -- delims)))

  def NL = rule { Spaces ~ optional('\r') ~ '\n' ~ Spaces }

  def Spaces = rule { zeroOrMore(' ') }

  def Separator = rule { Spaces ~ delimeter ~ Spaces }

}

case class DataTable(headers: Headers, rows: Seq[Row]) {
  require(rows.forall(_.fields.size == headers.fields.size), "Datatable is malformed, all rows must have the same number of elements")

  def asMap: Map[String, Seq[Json]] =
    headers.fields.zipWithIndex.map {
      case (header, index) ⇒
        header → rows.map(r ⇒ r.fields(index))
    }.groupBy(_._1).map { case (k, v) ⇒ (k, v.flatMap(_._2)) }

  def objectList: List[JsonObject] = {
    val tmp = for (i ← rows.indices) yield asMap.map { case (k, v) ⇒ k → v(i) }
    tmp.map(JsonObject.fromMap).toList
  }
}

case class Headers(fields: Seq[String])
case class Row(fields: Seq[Json])