package com.github.agourlay.cornichon.core.dsl

import org.parboiled2._

trait DataTableDsl {
  this: Dsl ⇒

  def parseDataTable(input: String) = {
    new DataTableParser(input).dataTableRule.run()
  }
}

class DataTableParser(val input: ParserInput) extends Parser {

  val delimeter = '|'

  def dataTableRule = rule {
    optional(NL) ~ RowRule ~ NL ~ oneOrMore(RowRule).separatedBy(NL) ~ optional(NL) ~ EOI ~> DataTable
  }

  def RowRule = rule { Separator ~ oneOrMore(TXT).separatedBy(Separator) ~ Separator ~> (x ⇒ Row(x)) }

  val delims = s"$delimeter\r\n"

  def TXT = rule(capture(oneOrMore(CharPredicate.Visible -- delims)))

  def NL = rule { Spaces ~ optional('\r') ~ '\n' ~ Spaces }

  def Spaces = rule { zeroOrMore(' ') }

  def Separator = rule { Spaces ~ delimeter ~ Spaces }

}

case class DataTable(header: Row, rows: Seq[Row]) {
  require(rows.forall(_.fields.size == header.fields.size), "Datatable is malformed, all rows must have the same number of elements")
}

case class Row(fields: Seq[String])