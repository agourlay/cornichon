package com.github.agourlay.cornichon.json

import io.circe.{ Json, JsonObject }
import cats.instances.string._
import org.scalatest.{ Matchers, OptionValues, WordSpec }
import com.github.agourlay.cornichon.json.JsonPath._

class CornichonJsonSpec extends WordSpec
  with Matchers
  with CornichonJson
  with OptionValues {

  def refParser(input: String) =
    io.circe.parser.parse(input).fold(e ⇒ throw e, identity)

  def mapToJsonObject(m: Map[String, Json]) =
    Json.fromJsonObject(JsonObject.fromMap(m))

  def parseUnsafe(path: String) =
    parse(path).valueUnsafe

  "CornichonJson" when {
    "parseJson" must {

      "parse JSON object string" in {
        val expected = mapToJsonObject(Map("name" → Json.fromString("cornichon")))
        parseDslJson("""{"name":"cornichon"}""") should be(Right(expected))
      }

      "parse JSON object string with spaces" in {
        val expected = mapToJsonObject(Map("name" → Json.fromString("cornichon")))
        parseDslJson("""  {"name":"cornichon"}  """) should be(Right(expected))
      }

      "parse JSON Array string" in {
        val expected = Json.fromValues(Seq(
          mapToJsonObject(Map("name" → Json.fromString("cornichon"))),
          mapToJsonObject(Map("name" → Json.fromString("scala")))
        ))

        parseDslJson(
          """
           [
            {"name":"cornichon"},
            {"name":"scala"}
           ]
           """
        ) should be(Right(expected))
      }

      "parse data table" in {
        val expected =
          """
            |[
            |{
            |"2LettersName" : false,
            | "Age": 50,
            | "Name": "John"
            |},
            |{
            |"2LettersName" : true,
            | "Age": 11,
            | "Name": "Bob"
            |}
            |]
          """.stripMargin

        parseDslJson("""
           |  Name  |   Age  | 2LettersName |
           | "John" |   50   |    false     |
           | "Bob"  |   11   |    true      |
         """) should be(Right(refParser(expected)))
      }

      "parse data table with empty cell values" in {
        parseDataTable(
          """
            |  Name  |   Age  | 2LettersName |
            |        |        |    false     |
            | "Bob"  |   11   |              |
          """
        ) should be(Right(List(
            """
            {
              "2LettersName" : false
            }
          """,
            """
            {
              "Age": 11,
              "Name": "Bob"
            }
          """) map (refParser(_).asObject.value)))
      }

      "parse data table as a map of raw string values" in {
        parseDataTableRaw(
          """
            | Name |   Age  | 2LettersName |
            |      |        |    false     |
            | Bob  |   11   |              |
          """
        ) should be(Right(List(
            Map("2LettersName" → "false"),
            Map("Age" → "11", "Name" → "Bob"))))
      }
    }

    "isJsonString" must {

      "detect invalid empty string" in {
        isJsonString("") should be(false)
      }

      "detect a string" in {
        isJsonString("a") should be(true)
      }

      "detect an object" in {
        isJsonString(""" { "a" : "v"} """) should be(false)
      }

      "detect an array" in {
        isJsonString(""" [ "a", "v"] """) should be(false)
      }

    }

    "removeFieldsByPath" must {

      "remove everything if root path" in {
        val input =
          """
            |{
            |"2LettersName" : false,
            | "Age": 50,
            | "Name": "John"
            |}
          """.stripMargin

        removeFieldsByPath(refParser(input), Seq(rootPath)) should be(Json.Null)
      }

      "remove nothing if path does not exist" in {
        val input =
          """
            |{
            |"2LettersName" : false,
            | "Age": 50,
            | "Name": "John"
            |}
          """.stripMargin

        removeFieldsByPath(refParser(input), parseUnsafe("blah") :: Nil) should be(refParser(input))
      }

      "remove root keys" in {
        val input =
          """
            |{
            |"2LettersName" : false,
            | "Age": 50,
            | "Name": "John"
            |}
          """.stripMargin

        val expected =
          """
          |{
          | "Age": 50
          |}
        """.stripMargin
        val paths = Seq("2LettersName", "Name").map(parseUnsafe)
        removeFieldsByPath(refParser(input), paths) should be(refParser(expected))
      }

      "remove only root keys" in {
        val input =
          """
            |{
            |"name" : "bob",
            |"age": 50,
            |"brothers":[
            |  {
            |    "name" : "john",
            |    "age": 40
            |  }
            |]
            |} """.stripMargin

        val expected = """
           |{
           |"age": 50,
           |"brothers":[
           |  {
           |    "name" : "john",
           |    "age": 40
           |  }
           |]
           |} """.stripMargin

        val paths = Seq("name").map(parseUnsafe)
        removeFieldsByPath(refParser(input), paths) should be(refParser(expected))
      }

      "remove keys inside specific indexed element" in {
        val input =
          """
            |{
            |"name" : "bob",
            |"age": 50,
            |"brothers":[
            |  {
            |    "name" : "john",
            |    "age": 40
            |  },
            |  {
            |    "name" : "jim",
            |    "age": 30
            |  }
            |]
            |}
          """.stripMargin

        val expected = """
          |{
          |"name" : "bob",
          |"age": 50,
          |"brothers":[
          |  {
          |    "age": 40
          |  },
          |  {
          |    "name" : "jim",
          |    "age": 30
          |  }
          |]
          |} """.stripMargin

        val paths = Seq("brothers[0].name").map(parseUnsafe)
        removeFieldsByPath(refParser(input), paths) should be(refParser(expected))
      }

      //FIXME - done manually in BodyArrayAssertion for now
      "remove field in each element of a root array" ignore {

        val input =
          """
            |[
            |{
            |  "name" : "bob",
            |  "age": 50
            |},
            |{
            |  "name" : "jim",
            |  "age": 40
            |},
            |{
            |  "name" : "john",
            |  "age": 30
            |}
            |]
          """.stripMargin

        val expected =
          """
            |[
            |{
            |  "name" : "bob"
            |},
            |{
            |  "name" : "jim"
            |},
            |{
            |  "name" : "john"
            |}
            |]
          """.stripMargin

        val paths = Seq("age").map(parseUnsafe)
        removeFieldsByPath(refParser(input), paths) should be(Right(refParser(expected)))
      }

      //FIXME - done manually in BodyArrayAssertion for now
      "remove field in each element of a nested array" ignore {

        val input =
          """
            |{
            |"people":[
            |{
            |  "name" : "bob",
            |  "age": 50
            |},
            |{
            |  "name" : "jim",
            |  "age": 40
            |},
            |{
            |  "name" : "john",
            |  "age": 30
            |}
            |]
            |}
          """.stripMargin

        val expected =
          """
            |{
            |"people":[
            |{
            |  "name" : "bob"
            |},
            |{
            |  "name" : "jim"
            |},
            |{
            |  "name" : "john"
            |}
            |]
            |}
          """.stripMargin

        val paths = Seq("people[*].age").map(parseUnsafe)
        removeFieldsByPath(refParser(input), paths) should be(Right(refParser(expected)))
      }

      "be correct even with duplicate Fields" in {

        val input =
          """
            |{
            |"name" : "bob",
            |"age": 50,
            |"brother":[
            |  {
            |    "name" : "john",
            |    "age": 40
            |  }
            |],
            |"friend":[
            |  {
            |    "name" : "john",
            |    "age": 30
            |  }
            |]
            |}
          """.stripMargin

        val expected =
          """
            |{
            |"name" : "bob",
            |"age": 50,
            |"brother":[
            |  {
            |    "age": 40
            |  }
            |],
            |"friend":[
            |  {
            |    "name" : "john",
            |    "age": 30
            |  }
            |]
            |}
          """.stripMargin

        val paths = Seq("brother[0].name").map(parseUnsafe)

        removeFieldsByPath(refParser(input), paths) should be(refParser(expected))
      }
    }

    "parseGraphQLJson" must {
      "nominal case" in {
        val in = """
        {
          id: 1
          name: "door"
          items: [
            # pretty broken door
            {state: Open, durability: 0.1465645654675762354763254763343243242}
            null
            {state: Open, durability: 0.5, foo: null}
          ]
        }
        """

        val expected = """
        {
          "id": 1,
          "name": "door",
          "items": [
            {"state": "Open", "durability": 0.1465645654675762354763254763343243242},
            null,
            {"state": "Open", "durability": 0.5, "foo": null}
          ]
        }
        """

        val out = parseGraphQLJson(in)
        out should be(Right(refParser(expected)))
      }
    }

    "whitelistingValue" must {
      "detect correct whitelisting on simple object" in {
        val actual =
          """
            |{
            |"2LettersName" : false,
            | "Age": 50,
            | "Name": "John"
            |}
          """.stripMargin
        val actualJson = parseDslJsonUnsafe(actual)

        val input =
          """
            |{
            |"2LettersName" : false,
            | "Age": 50
            |}
          """.stripMargin
        val inputJson = parseDslJsonUnsafe(input)

        whitelistingValue(inputJson, actualJson) should be(Right(actualJson))
      }

      "detect incorrect whitelisting on simple object" in {
        val actual =
          """
            |{
            |"2LettersName" : false,
            | "Age": 50,
            | "Name": "John"
            |}
          """.stripMargin
        val actualJson = parseDslJsonUnsafe(actual)

        val input =
          """
            |{
            |"2LettersName" : false,
            | "Ag": 50
            |}
          """.stripMargin
        val inputJson = parseDslJsonUnsafe(input)

        whitelistingValue(inputJson, actualJson) should be(Left(WhitelistingError(Seq("/Ag"), actualJson)))
      }

      "detect correct whitelisting on root array" in {

        val actual =
          """
            |[
            |{
            |"2LettersName" : false,
            | "Age": 50,
            | "Name": "John"
            |},
            |{
            |"2LettersName" : true,
            | "Age": 11,
            | "Name": "Bob"
            |}
            |]
          """.stripMargin
        val actualJson = parseDslJsonUnsafe(actual)

        val input =
          """
            |[
            |{
            |"2LettersName" : false,
            | "Name": "John"
            |},
            |{
            |"2LettersName" : true,
            | "Name": "Bob"
            |}
            |]
          """.stripMargin
        val inputJson = parseDslJsonUnsafe(input)

        whitelistingValue(inputJson, actualJson) should be(Right(actualJson))
      }

      "detect incorrect whitelisting on root array" in {

        val actual =
          """
            |[
            |{
            |"2LettersName" : false,
            | "Age": 50,
            | "Name": "John"
            |},
            |{
            |"2LettersName" : true,
            | "Age": 11,
            | "Name": "Bob"
            |}
            |]
          """.stripMargin
        val actualJson = parseDslJsonUnsafe(actual)

        val input =
          """
            |[
            |{
            |"2LettersName" : false,
            | "Nam": "John"
            |},
            |{
            |"2LettersName" : true,
            | "Name": "Bob"
            |}
            |]
          """.stripMargin
        val inputJson = parseDslJsonUnsafe(input)

        whitelistingValue(inputJson, actualJson) should be(Left(WhitelistingError(Seq("/0/Nam"), actualJson)))
      }
    }

    "findAllContainingValue" must {

      "handle empty values array" in {
        val input = "target value"
        findAllPathWithValue(Nil, parseDslJsonUnsafe(input)) should be(Nil)
      }

      "find root value" in {

        val input = "target value"

        findAllPathWithValue("target value" :: Nil, parseDslJsonUnsafe(input)) should be(List(rootPath))
      }

      "not find root value" in {

        val input = "target values"

        findAllPathWithValue("target value" :: Nil, parseDslJsonUnsafe(input)) should be(Nil)
      }

      "find root key" in {

        val input =
          """
            |{
            |"2LettersName" : false,
            | "Age": 50,
            | "Name": "John"
            |}
          """.stripMargin

        findAllPathWithValue("John" :: Nil, parseDslJsonUnsafe(input)) should be(List(parseUnsafe("$.Name")))

      }

      "find nested key" in {

        val input =
          """
            |{
            | "2LettersName" : false,
            | "Age": 50,
            | "Name": "John",
            | "Brother": {
            |   "Name" : "Paul",
            |   "Age": 50
            | }
            |}
          """.stripMargin

        findAllPathWithValue("Paul" :: Nil, parseDslJsonUnsafe(input)) should be(List(parseUnsafe("$.Brother.Name")))

      }

      "find key in array" in {

        val input =
          """
            |{
            | "2LettersName": false,
            | "Age": 50,
            | "Name": "John",
            | "Brothers": [
            |   {
            |     "Name" : "Paul",
            |     "Age": 50
            |   },
            |   {
            |     "Name": "Bob",
            |     "Age" : 30
            |   }
            | ]
            |}
          """.stripMargin

        findAllPathWithValue("Bob" :: Nil, parseDslJsonUnsafe(input)) should be(List(parseUnsafe("$.Brothers[1].Name")))

      }

      "find key in array of strings" in {

        val input =
          """
            |{
            | "2LettersName" : false,
            | "Age": 50,
            | "Name": "John",
            | "Hobbies": [ "Basketball", "Climbing", "Coding"]
            |}
          """.stripMargin

        findAllPathWithValue("Coding" :: Nil, parseDslJsonUnsafe(input)) should be(List(parseUnsafe("$.Hobbies[2]")))

      }
    }
  }
}
