package com.github.agourlay.cornichon.json

import io.circe.{ Json, JsonObject }
import com.github.agourlay.cornichon.json.JsonPath._
import utest._

object CornichonJsonSpec extends TestSuite with CornichonJson {

  def refParser(input: String): Json =
    io.circe.parser.parse(input).fold(e => throw e, identity)

  def mapToJsonObject(m: Map[String, Json]): Json =
    Json.fromJsonObject(JsonObject.fromMap(m))

  def parseUnsafe(path: String): JsonPath =
    parse(path).valueUnsafe

  val tests = Tests {
    test("parseJson object String") {
      val expected = mapToJsonObject(Map("name" -> Json.fromString("cornichon")))
      assert(parseDslJson("""{"name":"cornichon"}""") == Right(expected))
    }

    test("parseJson object String with spaces") {
      val expected = mapToJsonObject(Map("name" -> Json.fromString("cornichon")))
      assert(parseDslJson("""   {"name":"cornichon"}""") == Right(expected))
    }

    test("parseJson JSON Array string") {
      val expected = Json.fromValues(Seq(
        mapToJsonObject(Map("name" -> Json.fromString("cornichon"))),
        mapToJsonObject(Map("name" -> Json.fromString("scala")))
      ))

      assert(parseDslJson("""[ {"name":"cornichon"}, {"name":"scala"} ]""") == Right(expected))
    }

    test("parseJson data-table") {
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

      assert(parseDslJson("""
                     |  Name  |   Age  | 2LettersName |
                     | "John" |   50   |    false     |
                     | "Bob"  |   11   |    true      |
         """) == Right(refParser(expected)))
    }

    test("parseJson data table with empty cell values") {
      val parsed = parseDataTable(
        """
          |  Name  |   Age  | 2LettersName |
          |        |        |    false     |
          | "Bob"  |   11   |              |
          """
      )

      assert(parsed == Right(List(
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
          """) map (refParser(_).asObject.get)))
    }

    test("parseJson parse data table as a map of raw string values") {
      assert(parseDataTableRaw(
        """
          | Name |   Age  | 2LettersName |
          |      |        |    false     |
          | Bob  |   11   |              |
          """
      ) == Right(List(
          Map("2LettersName" -> "false"),
          Map("Age" -> "11", "Name" -> "Bob"))))
    }

    test("isJsonString detects invalid empty string") {
      assert(!isJsonString(""))
    }

    test("isJsonString detects a string") {
      assert(isJsonString("a"))
    }

    test("isJsonString detects an object") {
      assert(!isJsonString(""" { "a" : "v"} """))
    }

    test("isJsonString detects an array") {
      assert(!isJsonString(""" [ "a", "v"] """))
    }

    test("removeFieldsByPath removes everything if root path") {
      val input =
        """
          |{
          |"2LettersName" : false,
          | "Age": 50,
          | "Name": "John"
          |}
          """.stripMargin

      assert(removeFieldsByPath(refParser(input), Seq(rootPath)) == Json.Null)
    }

    test("removeFieldsByPath removes nothing if path does not exist") {
      val input =
        """
          |{
          |"2LettersName" : false,
          | "Age": 50,
          | "Name": "John"
          |}
          """.stripMargin

      assert(removeFieldsByPath(refParser(input), parseUnsafe("blah") :: Nil) == refParser(input))
    }

    test("removeFieldsByPath removes root keys") {
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
      assert(removeFieldsByPath(refParser(input), paths) == refParser(expected))
    }

    test("removeFieldsByPath removes only root keys") {
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
      assert(removeFieldsByPath(refParser(input), paths) == refParser(expected))
    }

    test("removeFieldsByPath removes keys inside specific indexed element") {
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
      assert(removeFieldsByPath(refParser(input), paths) == refParser(expected))
    }

    //FIXME - done manually in BodyArrayAssertion for now
    //    test("removeFieldsByPath removes field in each element of a root array") {
    //      val input =
    //        """
    //          |[
    //          |{
    //          |  "name" : "bob",
    //          |  "age": 50
    //          |},
    //          |{
    //          |  "name" : "jim",
    //          |  "age": 40
    //          |},
    //          |{
    //          |  "name" : "john",
    //          |  "age": 30
    //          |}
    //          |]
    //          """.stripMargin
    //
    //      val expected =
    //        """
    //          |[
    //          |{
    //          |  "name" : "bob"
    //          |},
    //          |{
    //          |  "name" : "jim"
    //          |},
    //          |{
    //          |  "name" : "john"
    //          |}
    //          |]
    //          """.stripMargin
    //
    //      val paths = Seq("age").map(parseUnsafe)
    //      assert(removeFieldsByPath(refParser(input), paths) == Right(refParser(expected)))
    //    }

    //FIXME - done manually in BodyArrayAssertion for now
    //    test("removeFieldsByPath removes field in each element of a nested array") {
    //      val input =
    //        """
    //          |{
    //          |"people":[
    //          |{
    //          |  "name" : "bob",
    //          |  "age": 50
    //          |},
    //          |{
    //          |  "name" : "jim",
    //          |  "age": 40
    //          |},
    //          |{
    //          |  "name" : "john",
    //          |  "age": 30
    //          |}
    //          |]
    //          |}
    //          """.stripMargin
    //
    //      val expected =
    //        """
    //          |{
    //          |"people":[
    //          |{
    //          |  "name" : "bob"
    //          |},
    //          |{
    //          |  "name" : "jim"
    //          |},
    //          |{
    //          |  "name" : "john"
    //          |}
    //          |]
    //          |}
    //          """.stripMargin
    //
    //      val paths = Seq("people[*].age").map(parseUnsafe)
    //      assert(removeFieldsByPath(refParser(input), paths) == Right(refParser(expected)))
    //    }

    test("removeFieldsByPath is correct even with duplicate Fields") {
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
      assert(removeFieldsByPath(refParser(input), paths) == refParser(expected))
    }

    test("parseGraphQLJson nominal case") {
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
      assert(out == Right(refParser(expected)))
    }

    test("findAllContainingValue handles empty values array") {
      val input = "target value"
      assert(findAllPathWithValue(Nil, parseDslJsonUnsafe(input)) == Nil)
    }

    test("findAllContainingValue find root value") {
      val input = "target value"
      assert(findAllPathWithValue("target value" :: Nil, parseDslJsonUnsafe(input)) == List(rootPath))
    }

    test("findAllContainingValue not find root value") {
      val input = "target values"
      assert(findAllPathWithValue("target value" :: Nil, parseDslJsonUnsafe(input)) == Nil)
    }

    test("findAllContainingValue find root key") {
      val input =
        """
          |{
          |"2LettersName" : false,
          | "Age": 50,
          | "Name": "John"
          |}
          """.stripMargin

      assert(findAllPathWithValue("John" :: Nil, parseDslJsonUnsafe(input)) == List(parseUnsafe("$.Name")))
    }

    test("findAllContainingValue finds nested key") {
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

      assert(findAllPathWithValue("Paul" :: Nil, parseDslJsonUnsafe(input)) == List(parseUnsafe("$.Brother.Name")))
    }

    test("findAllContainingValue finds key in array") {
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

      assert(findAllPathWithValue("Bob" :: Nil, parseDslJsonUnsafe(input)) == List(parseUnsafe("$.Brothers[1].Name")))
    }

    test("findAllContainingValue finds key in array of strings") {
      val input =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John",
          | "Hobbies": [ "Basketball", "Climbing", "Coding"]
          |}
          """.stripMargin

      assert(findAllPathWithValue("Coding" :: Nil, parseDslJsonUnsafe(input)) == List(parseUnsafe("$.Hobbies[2]")))
    }
  }
}
