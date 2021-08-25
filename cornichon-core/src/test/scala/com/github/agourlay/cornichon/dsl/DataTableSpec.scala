package com.github.agourlay.cornichon.dsl

import io.circe.Json
import com.github.agourlay.cornichon.json.CornichonJson.parseDataTable
import munit.FunSuite

class DataTableSpec extends FunSuite {

  def referenceParser(input: String): Json = io.circe.parser.parse(input).fold(e => throw e, identity)

  test("parser reports malformed JSON") {
    val input = """
                    |   Name  |   Age   |
                    |  "John" |   5a    |
                  """

    assert(parseDataTable(input).isLeft)
  }

  test("parser processes a single line with 1 value without new line on first") {
    val input = """ |   Name   |
                      |  "John"  |
                  """

    parseDataTable(input) == Right(
      List(Json.obj("Name" -> Json.fromString("John")).asObject.get))
  }

  test("parser processes a single line with 1 value") {
    val input = """
                    |   Name   |
                    |  "John"  |
                  """

    parseDataTable(input) == Right(
      List(Json.obj("Name" -> Json.fromString("John")).asObject.get))
  }

  test("parser processes a single line with 2 values") {
    val input = """
                    |   Name  |   Age   |
                    |  "John" |   50    |
                  """

    parseDataTable(input) == Right(
      List(Json.obj(
        "Name" -> Json.fromString("John"),
        "Age" -> Json.fromInt(50)
      ).asObject.get))
  }

  test("parser processes string values and headers with unicode characters and escaping") {
    val input =
      """
          | Name                  |   Größe \u00DF " \| test |
          | "öÖß \u00DF \" test " |   50                     |
        """

    parseDataTable(input) == Right(
      List(Json.obj(
        "Name" -> Json.fromString("öÖß \u00DF \" test "),
        "Größe \u00DF \" | test" -> Json.fromInt(50)
      ).asObject.get))
  }

  test("parser processes multi-line string") {
    val input = """
                    |  Name  |   Age  |
                    | "John" |   50   |
                    | "Bob"  |   11   |
      """

    parseDataTable(input) == Right(
      List(
        Json.obj(
          "Name" -> Json.fromString("John"),
          "Age" -> Json.fromInt(50)
        ).asObject.get,
        Json.obj(
          "Name" -> Json.fromString("Bob"),
          "Age" -> Json.fromInt(11)
        ).asObject.get))
  }

  test("parser detects malformed tables") {
    val input = """
                    |  Name   |   Age  |
                    | "John"  |   50   | "blah" |
      """

    parseDataTable(input) match {
      case Right(_) => assert(false)
      case Left(e)  => assert(e.renderedMessage.contains("requirement failed: the data table is malformed, all rows must have the same number of elements"))
    }
  }

  test("parser produces valid Json Array") {
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

    val objects = parseDataTable(input).map(l => l.map(Json.fromJsonObject)).map(Json.fromValues)
    assert(objects == Right(referenceParser(expected)))
  }
}
