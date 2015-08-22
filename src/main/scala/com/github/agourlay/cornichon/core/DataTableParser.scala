package com.github.agourlay.cornichon.core

import org.parboiled2._
import spray.json.{ JsArray, JsObject, JsValue }

import scala.util.{ Failure, Success }

object DataTableParser {
  def parseDataTable(input: String) = {
    val p = new DataTableParser(input)
    p.dataTableRule.run() match {
      case Failure(e: ParseError) ⇒
        println(p.formatError(e, new ErrorFormatter(showTraces = true)))
        throw new DataTableError(p.formatError(e, new ErrorFormatter(showTraces = true)))
      case Success(dt) ⇒ dt
    }
  }
}

class DataTableParser(val input: ParserInput) extends Parser {

  val delimeter = '|'

  def dataTableRule = rule {
    optional(NL) ~ HeaderRule ~ NL ~ oneOrMore(RowRule).separatedBy(NL) ~ optional(NL) ~ EOI ~> DataTable
  }

  import spray.json._

  def HeaderRule = rule { Separator ~ oneOrMore(HeaderTXT).separatedBy(Separator) ~ Separator ~> Headers }

  def RowRule = rule { Separator ~ oneOrMore(TXT).separatedBy(Separator) ~ Separator ~> (x ⇒ Row(x.map(_.parseJson))) }

  val delims = s"$delimeter\r\n"

  def HeaderTXT = rule(capture(oneOrMore(CharPredicate.Visible -- delims)))

  def TXT = rule(capture(oneOrMore(CharPredicate.Printable -- delims)))

  def NL = rule { Spaces ~ optional('\r') ~ '\n' ~ Spaces }

  def Spaces = rule { zeroOrMore(' ') }

  def Separator = rule { Spaces ~ delimeter ~ Spaces }

}

case class DataTable(headers: Headers, rows: Seq[Row]) {
  require(rows.forall(_.fields.size == headers.fields.size), "Datatable is malformed, all rows must have the same number of elements")

  def asMap: Map[String, Seq[JsValue]] =
    headers.fields.zipWithIndex.map {
      case (header, index) ⇒
        header → rows.map(r ⇒ r.fields(index))
    }.groupBy(_._1).map { case (k, v) ⇒ (k, v.flatMap(_._2)) }

  def asJson: JsArray = {
    val map = asMap
    val tmp = for (i ← rows.indices) yield map.mapValues(v ⇒ v(i))
    JsArray(tmp.map(JsObject(_)).toVector)
  }
}

case class Headers(fields: Seq[String])
case class Row(fields: Seq[JsValue])