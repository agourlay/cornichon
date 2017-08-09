package com.github.agourlay.cornichon.json

import io.circe.{ Json, JsonObject }
import cats.instances.string._
import cats.instances.long._
import cats.instances.double._
import cats.instances.boolean._
import cats.instances.int._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{ Matchers, WordSpec }
import cats.instances.bigDecimal._
import cats.scalatest.{ EitherMatchers, EitherValues }

class CornichonJsonSpec extends WordSpec
  with Matchers
  with PropertyChecks
  with CornichonJson
  with EitherValues
  with EitherMatchers {

  def refParser(input: String) =
    io.circe.parser.parse(input).fold(e ⇒ throw e, identity)

  def mapToJsonObject(m: Map[String, Json]) =
    Json.fromJsonObject(JsonObject.fromMap(m))

  "CornichonJson" when {
    "parseJson" must {
      "parse Boolean" in {
        forAll { bool: Boolean ⇒
          parseJson(bool) should beRight(Json.fromBoolean(bool))
        }
      }

      "parse Int" in {
        forAll { int: Int ⇒
          parseJson(int) should beRight(Json.fromInt(int))
        }
      }

      "parse Long" in {
        forAll { long: Long ⇒
          parseJson(long) should beRight(Json.fromLong(long))
        }
      }

      "parse Double" in {
        forAll { double: Double ⇒
          parseJson(double) should beRight(Json.fromDoubleOrNull(double))
        }
      }

      "parse BigDecimal" in {
        forAll { bigDec: BigDecimal ⇒
          parseJson(bigDec) should beRight(Json.fromBigDecimal(bigDec))
        }
      }

      "parse flat string" in {
        parseJson("cornichon") should beRight(Json.fromString("cornichon"))
      }

      "parse JSON object string" in {
        val expected = mapToJsonObject(Map("name" → Json.fromString("cornichon")))
        parseJson("""{"name":"cornichon"}""") should beRight(expected)
      }

      "parse JSON Array string" in {
        val expected = Json.fromValues(Seq(
          mapToJsonObject(Map("name" → Json.fromString("cornichon"))),
          mapToJsonObject(Map("name" → Json.fromString("scala")))
        ))

        parseJson(
          """
           [
            {"name":"cornichon"},
            {"name":"scala"}
           ]
           """
        ) should beRight(expected)
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

        parseJson("""
           |  Name  |   Age  | 2LettersName |
           | "John" |   50   |    false     |
           | "Bob"  |   11   |    true      |
         """) should beRight(refParser(expected))
      }
    }

    "removeFieldsByPath" must {
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
        val paths = Seq("2LettersName", "Name").map(JsonPath.parseUnsafe)
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

        val paths = Seq("name").map(JsonPath.parseUnsafe)
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

        val paths = Seq("brothers[0].name").map(JsonPath.parseUnsafe)
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

        val paths = Seq("age").map(JsonPath.parseUnsafe)
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

        val paths = Seq("people[*].age").map(JsonPath.parseUnsafe)
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

        val paths = Seq("brother[0].name").map(JsonPath.parseUnsafe)

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
        out should beRight(refParser(expected))
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
        val actualJson = parseJsonUnsafe(actual)

        val input =
          """
            |{
            |"2LettersName" : false,
            | "Age": 50
            |}
          """.stripMargin
        val inputJson = parseJsonUnsafe(input)

        whitelistingValue(inputJson, actualJson).value shouldBe actualJson
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
        val actualJson = parseJsonUnsafe(actual)

        val input =
          """
            |{
            |"2LettersName" : false,
            | "Ag": 50
            |}
          """.stripMargin
        val inputJson = parseJsonUnsafe(input)

        whitelistingValue(inputJson, actualJson) should beLeft(WhitelistingError(Seq("/Ag"), actualJson))
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
        val actualJson = parseJsonUnsafe(actual)

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
        val inputJson = parseJsonUnsafe(input)

        whitelistingValue(inputJson, actualJson).value shouldBe actualJson
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
        val actualJson = parseJsonUnsafe(actual)

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
        val inputJson = parseJsonUnsafe(input)

        whitelistingValue(inputJson, actualJson) should beLeft(WhitelistingError(Seq("/0/Nam"), actualJson))
      }
    }

    "findAllContainingValue" must {

      "find root key" in {

        val input =
          """
            |{
            |"2LettersName" : false,
            | "Age": 50,
            | "Name": "John"
            |}
          """.stripMargin

        findAllJsonWithValue(List("John"), parseJsonUnsafe(input)) should be(List(JsonPath.parseUnsafe("$.Name")))

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

        findAllJsonWithValue(List("Paul"), parseJsonUnsafe(input)) should be(List(JsonPath.parseUnsafe("$.Brother.Name")))

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

        findAllJsonWithValue(List("Bob"), parseJsonUnsafe(input)) should be(List(JsonPath.parseUnsafe("$.Brothers[1].Name")))

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

        findAllJsonWithValue(List("Coding"), parseJsonUnsafe(input)) should be(List(JsonPath.parseUnsafe("$.Hobbies[2]")))

      }
    }
  }
}
