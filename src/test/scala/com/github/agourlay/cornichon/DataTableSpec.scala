package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.core.dsl.{ Row, DataTable, DataTableParser }
import org.parboiled2.{ ErrorFormatter, ParseError }
import org.scalatest.{ Matchers, WordSpec }

import scala.util._

class DataTableSpec extends WordSpec with Matchers {

  "DataTable parser" must {

    "process a single line with 1 value without new line on first" in {
      val input = """ |  Key  |
                      |  k-1  |
                  """

      val expected = DataTable(
        header = Row(Seq("Key")),
        rows = Seq(
          Row(Seq("k-1"))
        )
      )
      val p = new DataTableParser(input)
      p.dataTableRule.run() match {
        case Failure(e: ParseError) ⇒ fail(p.formatError(e, new ErrorFormatter(showTraces = true)))
        case Success(x)             ⇒ x should be(expected)
      }
    }

    "process a single line with 1 value" in {
      val input = """
        | Key |
        | k-1 |
        """

      val expected = DataTable(
        header = Row(Seq("Key")),
        rows = Seq(
          Row(Seq("k-1"))
        )
      )
      val p = new DataTableParser(input)
      p.dataTableRule.run() match {
        case Failure(e: ParseError) ⇒ fail(p.formatError(e, new ErrorFormatter(showTraces = true)))
        case Success(x)             ⇒ x should be(expected)
      }
    }

    "process a single line with 2 values" in {
      val input = """
        | Key | Value |
        | k-1 | v-1   |
      """

      val expected = DataTable(
        header = Row(Seq("Key", "Value")),
        rows = Seq(
          Row(Seq("k-1", "v-1"))
        )
      )
      val p = new DataTableParser(input)
      p.dataTableRule.run() match {
        case Failure(e: ParseError) ⇒ fail(p.formatError(e, new ErrorFormatter(showTraces = true)))
        case Success(x)             ⇒ x should be(expected)
      }
    }

    "process multiline string" in {
      val input = """
        | Key | Value |
        | k-1 | v-1   |
        | k-2 | v-2   |
      """

      val expected = DataTable(
        header = Row(Seq("Key", "Value")),
        rows = Seq(
          Row(Seq("k-1", "v-1")),
          Row(Seq("k-2", "v-2"))
        )
      )
      val p = new DataTableParser(input)
      p.dataTableRule.run() match {
        case Failure(e: ParseError) ⇒ fail(p.formatError(e))
        case Success(x)             ⇒ x should be(expected)
      }
    }
  }
}
