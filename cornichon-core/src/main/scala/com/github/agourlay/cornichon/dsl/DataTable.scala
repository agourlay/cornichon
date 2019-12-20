package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core.CornichonError
import org.parboiled2._

import scala.util.{ Failure, Success }

object DataTableParser {
  val WhiteSpace = CharPredicate("\u0009\u0020")

  val delimeter = CharPredicate('|')

  val delims = CharPredicate(delimeter, '\r', '\n')

  val Backslash = CharPredicate('\\')

  def parse(input: String): Either[CornichonError, DataTable] = {
    val p = new DataTableParser(input)
    p.dataTableRule.run() match {
      case Failure(e: ParseError) =>
        Left(DataTableParseError(p.formatError(e, new ErrorFormatter(showTraces = true))))
      case Failure(e: Throwable) =>
        Left(DataTableError(e, input))
      case Success(dt) =>
        Right(dt)
    }
  }
}

class DataTableParser(val input: ParserInput) extends Parser with StringHeaderParserSupport {
  def dataTableRule = rule {
    zeroOrMore(NL) ~ HeaderRule ~ NL ~ oneOrMore(RowRule).separatedBy(NL) ~ zeroOrMore(NL) ~ EOI ~> DataTable
  }

  def HeaderRule = rule { Separator ~ oneOrMore(HeaderValue).separatedBy(Separator) ~ Separator ~> Headers }

  def RowRule = rule { Separator ~ oneOrMore(CellContent).separatedBy(Separator) ~ Separator ~> Row }

  def CellContent = rule { !NL ~ capture(zeroOrMore(ContentsChar)) }

  def ContentsChar = rule { !DataTableParser.delims ~ ANY }

  def NL = rule { Spaces ~ optional('\r') ~ '\n' ~ Spaces }

  def Spaces = rule { quiet(zeroOrMore(DataTableParser.WhiteSpace)) }

  def Separator = rule { Spaces ~ DataTableParser.delimeter ~ Spaces }

}

case class DataTable(headers: Headers, rows: Seq[Row]) {
  require(rows.forall(_.fields.size == headers.fields.size), "The data table is malformed, all rows must have the same number of elements")

  lazy val rawStringList: List[Map[String, String]] =
    rows.toList
      .map { row =>
        headers.fields.zip(row.fields)
          .map { case (name, value) => name -> value.trim }
          .filter(_._2.nonEmpty)
          .toMap
      }
}

case class Headers(fields: Seq[String])
case class Row(fields: Seq[String])

trait StringHeaderParserSupport extends StringBuilding {
  this: Parser =>

  def HeaderValue = rule {
    atomic(clearSB() ~ Characters ~ push(sb.toString) ~> (_.trim))
  }

  def Characters = rule { oneOrMore(NormalChar | '\\' ~ EscapedChar) }

  def NormalChar = rule { !(DataTableParser.delims | DataTableParser.Backslash) ~ ANY ~ appendSB() }

  def EscapedChar = rule {
    DataTableParser.Backslash ~ appendSB() |
      'b' ~ appendSB('\b') |
      'f' ~ appendSB('\f') |
      'n' ~ appendSB('\n') |
      'r' ~ appendSB('\r') |
      't' ~ appendSB('\t') |
      '|' ~ appendSB('|') |
      Unicode ~> { code => sb.append(code.asInstanceOf[Char]); () }
  }

  def Unicode = rule { 'u' ~ capture(4 times CharPredicate.HexDigit) ~> (Integer.parseInt(_, 16)) }
}

case class DataTableError(error: Throwable, input: String) extends CornichonError {
  lazy val baseErrorMessage = s"error thrown '${error.getMessage}' while parsing data table $input"
}

case class DataTableParseError(baseErrorMessage: String) extends CornichonError
