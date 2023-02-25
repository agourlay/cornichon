package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core.CornichonError
import org.parboiled2._
import scala.collection.mutable.ListBuffer
import scala.util.{ Failure, Success }

object DataTableParser {
  val WhiteSpace = CharPredicate("\u0009\u0020")

  val delimiterChar = CharPredicate('|')

  val delims = CharPredicate(delimiterChar, '\r', '\n')

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

  def Separator = rule { Spaces ~ DataTableParser.delimiterChar ~ Spaces }

}

case class DataTable(headers: Headers, rows: Seq[Row]) {
  require(rows.forall(_.fields.size == headers.fields.size), "the data table is malformed, all rows must have the same number of elements")

  // TODO 0.21 return List[List] instead of List[Map] as the Map is not used
  lazy val rawStringList: List[Map[String, String]] = {
    val listBuffer = new ListBuffer[Map[String, String]]()
    for (row <- rows) {
      val mapBuilder = Map.newBuilder[String, String]
      for ((name, value) <- headers.fields.iterator.zip(row.fields.iterator)) {
        val stripped = value.stripTrailing()
        if (stripped.nonEmpty) {
          mapBuilder += name -> stripped
        }
      }
      listBuffer += mapBuilder.result()
    }
    listBuffer.toList
  }
}

case class Headers(fields: Seq[String])
case class Row(fields: Seq[String])

trait StringHeaderParserSupport extends StringBuilding {
  this: Parser =>

  def HeaderValue = rule {
    atomic(clearSB() ~ Characters ~ push(sb.toString) ~> (_.stripTrailing()))
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
