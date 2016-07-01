package com.github.agourlay.cornichon.dsl

import io.circe.{ Json, JsonObject }
import org.parboiled2._

import com.github.agourlay.cornichon.json.CornichonJson._

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

class DataTableParser(val input: ParserInput) extends Parser with StringHeaderParserSupport {
  val delimeter = CharPredicate('|')

  def dataTableRule = rule {
    zeroOrMore(NL) ~ HeaderRule ~ NL ~ oneOrMore(RowRule).separatedBy(NL) ~ zeroOrMore(NL) ~ EOI ~> DataTable
  }

  def HeaderRule = rule { Separator ~ oneOrMore(HeaderValue).separatedBy(Separator) ~ Separator ~> Headers }

  def RowRule = rule { Separator ~ oneOrMore(TXT).separatedBy(Separator) ~ Separator ~> (x ⇒ Row(x.map(parseString(_).fold(e ⇒ throw e, identity)))) }

  val delims = CharPredicate(delimeter, '\r', '\n')

  def TXT = rule { capture(oneOrMore(ContentsChar)) }

  def ContentsChar = rule { !delims ~ ANY }

  def NL = rule { Spaces ~ optional('\r') ~ '\n' ~ Spaces }

  val WhiteSpace = CharPredicate("\u0009\u0020")

  def Spaces = rule { quiet(zeroOrMore(WhiteSpace)) }

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

trait StringHeaderParserSupport extends StringBuilding {
  this: Parser ⇒

  def delims: CharPredicate

  def HeaderValue = rule {
    atomic(clearSB() ~ Characters ~ push(sb.toString) ~> (_.trim))
  }

  def Characters = rule { oneOrMore(NormalChar | '\\' ~ EscapedChar) }

  val Backslash = CharPredicate('\\')

  def NormalChar = rule { !(delims | Backslash) ~ ANY ~ appendSB() }

  def EscapedChar = rule {
    Backslash ~ appendSB() |
      'b' ~ appendSB('\b') |
      'f' ~ appendSB('\f') |
      'n' ~ appendSB('\n') |
      'r' ~ appendSB('\r') |
      't' ~ appendSB('\t') |
      '|' ~ appendSB('|') |
      Unicode ~> { code ⇒ sb.append(code.asInstanceOf[Char]); () }
  }

  def Unicode = rule { 'u' ~ capture(4 times CharPredicate.HexDigit) ~> (Integer.parseInt(_, 16)) }
}