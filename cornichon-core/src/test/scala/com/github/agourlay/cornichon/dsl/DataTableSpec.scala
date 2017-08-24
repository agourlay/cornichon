package com.github.agourlay.cornichon.dsl

import io.circe.Json
import org.parboiled2.ParseError
import org.scalatest.{Matchers, OptionValues, TryValues, WordSpec, EitherValues}

import scala.util.{Failure, Success}

class DataTableSpec extends WordSpec with Matchers with TryValues with OptionValues with EitherValues {

  def referenceParser(input: String) =
    io.circe.parser.parse(input).fold(e ⇒ throw e, identity)

  def parse(p: DataTableParser) = p.dataTableRule.run() match {
    case Success(dt)                ⇒ dt
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
      p.dataTableRule.run().map(_.objectList).isFailure should be(true)
    }

    "process a single line with 1 value without new line on first" in {
      val input = """ |   Name   |
                      |  "John"  |
                  """

      parse(new DataTableParser(input)).objectList should be(
        List(Json.obj("Name" → Json.fromString("John")).asObject.value))
    }

    "process a single line with 1 value" in {
      val input = """
        |   Name   |
        |  "John"  |
        """

      parse(new DataTableParser(input)).objectList should be(
        List(Json.obj("Name" → Json.fromString("John")).asObject.value))
    }

    "process a single line with 2 values" in {
      val input = """
        |   Name  |   Age   |
        |  "John" |   50    |
      """

      parse(new DataTableParser(input)).objectList should be(
        List(Json.obj(
          "Name" → Json.fromString("John"),
          "Age" → Json.fromInt(50)
        ).asObject.value))
    }

    "process string values nd headers with unicode characters and escaping" in {
      val input =
        """
          | Name                  |   Größe \u00DF " \| test |
          | "öÖß \u00DF \" test " |   50                     |
        """

      parse(new DataTableParser(input)).objectList should be(
        List(Json.obj(
          "Name" → Json.fromString("öÖß \u00DF \" test "),
          "Größe \u00DF \" | test" → Json.fromInt(50)
        ).asObject.value))
    }

    "process multiline string" in {
      val input = """
        |  Name  |   Age  |
        | "John" |   50   |
        | "Bob"  |   11   |
      """

      parse(new DataTableParser(input)).objectList should be(
        List(
          Json.obj(
            "Name" → Json.fromString("John"),
            "Age" → Json.fromInt(50)
          ).asObject.value,
          Json.obj(
            "Name" → Json.fromString("Bob"),
            "Age" → Json.fromInt(11)
          ).asObject.value))
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
      val objects = parse(p).objectList.right.value
      Json.fromValues(objects.map(Json.fromJsonObject)) should be(referenceParser(expected))
    }
  }
}
