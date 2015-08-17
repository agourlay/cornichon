package com.github.agourlay.cornichon.core.dsl

import org.parboiled2._
import spray.json.JsValue

trait DataTableDsl {
  this: Dsl ⇒

  def parseDataTable(input: String) = {
    new DataTableParser(input).dataTableRule.run()
  }
}

class DataTableParser(val input: ParserInput) extends Parser {

  val delimeter = '|'

  def dataTableRule = rule {
    optional(NL) ~ HeaderRule ~ NL ~ oneOrMore(RowRule).separatedBy(NL) ~ optional(NL) ~ EOI ~> DataTable
  }

  import spray.json._
  import spray.json.DefaultJsonProtocol._

  def HeaderRule = rule { Separator ~ oneOrMore(TXT).separatedBy(Separator) ~ Separator ~> Headers }

  def RowRule = rule { Separator ~ oneOrMore(TXT).separatedBy(Separator) ~ Separator ~> (x ⇒ Row(x.map(_.parseJson))) }

  val delims = s"$delimeter\r\n"

  def TXT = rule(capture(oneOrMore(CharPredicate.Visible -- delims)))

  def NL = rule { Spaces ~ optional('\r') ~ '\n' ~ Spaces }

  def Spaces = rule { zeroOrMore(' ') }

  def Separator = rule { Spaces ~ delimeter ~ Spaces }

}

case class DataTable(headers: Headers, rows: Seq[Row]) {
  require(rows.forall(_.fields.size == headers.fields.size), "Datatable is malformed, all rows must have the same number of elements")
}

case class Headers(fields: Seq[String])
case class Row(fields: Seq[JsValue])