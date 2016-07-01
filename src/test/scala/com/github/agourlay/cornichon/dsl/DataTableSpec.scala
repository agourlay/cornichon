package com.github.agourlay.cornichon.dsl

import io.circe.Json
import org.parboiled2.ParseError
import org.scalatest.{ Matchers, TryValues, WordSpec }

import scala.util.{ Failure, Success }

class DataTableSpec extends WordSpec with Matchers with TryValues {

  def referenceParser(input: String) =
    io.circe.parser.parse(input).fold(e ⇒ throw e, identity)

  def parse(p: DataTableParser) = p.dataTableRule.run() match {
    case Success(value)             ⇒ value
    case Failure(error: ParseError) ⇒ fail(p.formatError(error))
    case Failure(error)             ⇒ fail(error)
  }

  "DataTable parser" must {

    "report malformed JSON" in {
      val input = """
                    |   Name  |   Age   |
                    |  "John" |   5a    |
                  """

      val p = new DataTableParser(input)
      p.dataTableRule.run().isFailure should be(true)
    }

    "process a single line with 1 value without new line on first" in {
      val input = """ |   Name   |
                      |  "John"  |
                  """

      val expected = DataTable(
        headers = Headers(Seq("Name")),
        rows = Seq(
          Row(Seq(Json.fromString("John")))
        )
      )
      val p = new DataTableParser(input)
      parse(p) should be(expected)
    }

    "process a single line with 1 value" in {
      val input = """
        |   Name   |
        |  "John"  |
        """

      val expected = DataTable(
        headers = Headers(Seq("Name")),
        rows = Seq(
          Row(Seq(Json.fromString("John")))
        )
      )
      val p = new DataTableParser(input)
      parse(p) should be(expected)
    }

    "process a single line with 2 values" in {
      val input = """
        |   Name  |   Age   |
        |  "John" |   50    |
      """

      val expected = DataTable(
        headers = Headers(Seq("Name", "Age")),
        rows = Seq(
          Row(Seq(Json.fromString("John"), Json.fromInt(50)))
        )
      )
      val p = new DataTableParser(input)
      parse(p) should be(expected)
    }

    "process string values nd headers with unicode characters and escaping" in {
      val input =
        """
          | Name                  |   Größe \u00DF " \| test |
          | "öÖß \u00DF \" test " |   50                     |
        """

      val expected = DataTable(
        headers = Headers(Seq("Name", """Größe ß " | test""")),
        rows = Seq(
          Row(Seq(Json.fromString("""öÖß ß " test """), Json.fromInt(50)))
        )
      )

      val p = new DataTableParser(input)
      parse(p) should be(expected)
    }

    "process multiline string" in {
      val input = """
        |  Name  |   Age  |
        | "John" |   50   |
        | "Bob"  |   11   |
      """

      val expected = DataTable(
        headers = Headers(Seq("Name", "Age")),
        rows = Seq(
          Row(Seq(Json.fromString("John"), Json.fromInt(50))),
          Row(Seq(Json.fromString("Bob"), Json.fromInt(11)))
        )
      )
      val p = new DataTableParser(input)
      parse(p) should be(expected)
    }

    "notify malformed table" in {
      val input = """
        |  Name   |   Age  |
        | "John"  |   50   | "blah" |
      """

      val p = new DataTableParser(input)
      p.dataTableRule.run().failure.exception should have message "requirement failed: Datatable is malformed, all rows must have the same number of elements"
    }

    "produce valid Json Array" in {
      val input =
        """
          |  Name  |   Age  | 2LettersName |
          | "John" |   50   |    false     |
          | "Bob"  |   11   |    true      |
        """

      val expected = """
        [{
          "Name":"John",
          "Age":50,
          "2LettersName": false
        },
        {
          "Name":"Bob",
          "Age":11,
          "2LettersName": true
        }]
        """

      val p = new DataTableParser(input)
      val objects = parse(p).objectList
      Json.fromValues(objects.map(Json.fromJsonObject)) should be(referenceParser(expected))
    }
  }
}
