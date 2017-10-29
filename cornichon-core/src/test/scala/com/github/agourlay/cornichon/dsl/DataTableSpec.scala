package com.github.agourlay.cornichon.dsl

import cats.scalatest.EitherMatchers
import io.circe.Json
import org.scalatest.{ Matchers, OptionValues, WordSpec }
import com.github.agourlay.cornichon.json.CornichonJson.parseDataTable
import cats.syntax.either._

class DataTableSpec extends WordSpec
  with Matchers
  with OptionValues
  with EitherMatchers {

  def referenceParser(input: String) =
    io.circe.parser.parse(input).fold(e ⇒ throw e, identity)

  "DataTable parser" must {

    "report malformed JSON" in {
      val input = """
                    |   Name  |   Age   |
                    |  "John" |   5a    |
                  """

      parseDataTable(input) should be(left)
    }

    "process a single line with 1 value without new line on first" in {
      val input = """ |   Name   |
                      |  "John"  |
                  """

      parseDataTable(input) should beRight(
        List(Json.obj("Name" → Json.fromString("John")).asObject.value))
    }

    "process a single line with 1 value" in {
      val input = """
        |   Name   |
        |  "John"  |
        """

      parseDataTable(input) should beRight(
        List(Json.obj("Name" → Json.fromString("John")).asObject.value))
    }

    "process a single line with 2 values" in {
      val input = """
        |   Name  |   Age   |
        |  "John" |   50    |
      """

      parseDataTable(input) should beRight(
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

      parseDataTable(input) should beRight(
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

      parseDataTable(input) should beRight(
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

      parseDataTable(input) match {
        case Right(t) ⇒ fail(s"should have failed but got $t")
        case Left(e)  ⇒ e.renderedMessage should include("requirement failed: Datatable is malformed, all rows must have the same number of elements")
      }
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

      val objects = parseDataTable(input).map(l ⇒ l.map(Json.fromJsonObject)).map(Json.fromValues)
      objects should beRight(referenceParser(expected))
    }
  }
}
