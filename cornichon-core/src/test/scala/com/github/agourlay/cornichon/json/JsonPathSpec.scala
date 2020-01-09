package com.github.agourlay.cornichon.json

import io.circe.Json
import cats.syntax.show._
import utest._

object JsonPathSpec extends TestSuite {

  val tests = Tests {
    test("non strict version returns None if the field does not exist") {
      val input =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John"
          |}
        """.stripMargin

      assert(JsonPath.run("Name2", input) == Right(None))
    }

    test("select properly null field") {
      val input =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": null
          |}
        """.stripMargin

      assert(JsonPath.runStrict("Name", input) == Right(Json.Null))
    }

    test("select properly String based on single field") {
      val input =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John"
          |}
        """.stripMargin

      assert(JsonPath.runStrict("Name", input) == Right(Json.fromString("John")))
    }

    test("select properly Int based on single field") {
      val input =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John"
          |}
        """.stripMargin

      assert(JsonPath.runStrict("Age", input) == Right(Json.fromInt(50)))
    }

    test("select properly nested field in Object") {
      val input =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John",
          | "brother": {
          |   "Name" : "Paul",
          |   "Age": 50
          | }
          |}
        """.stripMargin

      assert(JsonPath.runStrict("brother.Age", input) == Right(Json.fromInt(50)))
    }

    test("select properly nested field in Array") {
      val input =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John",
          | "brothers": [
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

      assert(JsonPath.runStrict("brothers[1].Age", input) == Right(Json.fromInt(30)))
    }

    test("select properly nested fields projected in Array") {
      val input =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John",
          | "brothers": [
          |   {
          |     "Name" : "Paul",
          |     "Age": 50
          |   },
          |   {
          |     "Name": "Bob",
          |     "Age" : 30
          |   },
          |   {
          |     "Name": "Peter",
          |     "Age" : 20
          |   }
          | ]
          |}
        """.stripMargin

      assert(JsonPath.runStrict("brothers[*].Age", input) == Right(Json.arr(Json.fromInt(50), Json.fromInt(30), Json.fromInt(20))))
    }

    test("select properly nested fields projected in Array with a single value") {
      val input =
        """
          |{
          |  "2LettersName" : false,
          |  "Age": 50,
          |  "Name": "John",
          |  "brothers": [
          |    {
          |      "Name" : "Paul",
          |      "Age": 50
          |    }
          |  ]
          |}
        """.stripMargin

      assert(JsonPath.runStrict("brothers[*].Age", input) == Right(Json.arr(Json.fromInt(50))))
    }

    test("return empty array if projection on Array but nested field does not exist") {
      val input =
        """
          |{
          |  "2LettersName" : false,
          |  "Age": 50,
          |  "Name": "John",
          |  "brothers": [
          |    {
          |      "Name" : "Paul",
          |      "Age": 50
          |    }
          |  ]
          |}
        """.stripMargin

      assert(JsonPath.runStrict("brothers[*].age", input) == Right(Json.fromValues(Nil)))
    }

    test("return empty array if projection on empty Array but nested field does not exist") {
      val input =
        """
          |{
          |  "2LettersName" : false,
          |  "Age": 50,
          |  "Name": "John",
          |  "brothers": []
          |}
        """.stripMargin

      assert(JsonPath.runStrict("brothers[*].age", input) == Right(Json.fromValues(Nil)))
    }

    test("return None (nonStrict) if the array projected does not exist") {
      val input =
        """
          |{
          |  "2LettersName" : false,
          |  "Age": 50,
          |  "Name": "John",
          |  "brothers": [
          |    {
          |      "Name" : "Paul",
          |      "Age": 50
          |    }
          |  ]
          |}
        """.stripMargin

      assert(JsonPath.run("sisters[*].age", input) == Right(None))
    }

    test("select properly doubly nested fields projected in Array") {
      val input =
        """
          |{
          |  "2LettersName" : false,
          |  "Age": 50,
          |  "Name": "John",
          |  "Brothers": [
          |    {
          |      "Name" : "Paul",
          |      "Age": 50,
          |      "Hobbies" : [
          |        {
          |          "Name" : "Karate",
          |          "Level" : "Good"
          |        },{
          |          "Name" : "Football",
          |          "Level" : "Beginner"
          |        }
          |      ]
          |    },
          |    {
          |      "Name": "Bob",
          |      "Age" : 30,
          |      "Hobbies" : [
          |        {
          |          "Name" : "Diving",
          |          "Level" : "Good"
          |        },{
          |          "Name" : "Reading",
          |          "Level" : "Beginner"
          |        }
          |      ]
          |    }
          |  ]
          |}
        """.stripMargin

      assert(JsonPath.runStrict("Brothers[*].Hobbies[*].Name", input) == Right(Json.arr(
        Json.fromString("Karate"), Json.fromString("Football"), Json.fromString("Diving"), Json.fromString("Reading")
      )))
    }

    test("select properly projected elements of a root Array") {
      val input =
        """
          |[
          |  {
          |     "2LettersName" : false,
          |     "Age": 50,
          |     "Name": "John",
          |     "brothers": [
          |       {
          |         "Name" : "Paul",
          |         "Age": 20
          |       },
          |       {
          |         "Name": "Bob",
          |         "Age" : 30
          |       }
          |     ]
          |  },
          |  {
          |   "2LettersName" : false,
          |   "Age": 20,
          |   "Name": "Paul",
          |   "brothers": [
          |     {
          |       "Name" : "John",
          |       "Age": 50
          |     },
          |     {
          |       "Name": "Bob",
          |       "Age" : 30
          |     }
          |   ]
          |  },
          |  {
          |   "2LettersName" : false,
          |   "Age": 30,
          |   "Name": "Bob",
          |   "brothers": [
          |     {
          |       "Name" : "John",
          |       "Age": 50
          |     },
          |     {
          |       "Name": "Paul",
          |       "Age" : 20
          |     }
          |   ]
          |  }
          |]
        """.stripMargin

      val expected = JsonPath.runStrict("$[*].brothers[1].Age", input)
      val actual = Right(Json.arr(Json.fromInt(30), Json.fromInt(30), Json.fromInt(20)))
      assert(expected == actual)
    }

    test("select properly element of a root Array") {
      val input =
        """
          |[
          |  {
          |     "2LettersName" : false,
          |     "Age": 50,
          |     "Name": "John",
          |     "brothers": [
          |       {
          |         "Name" : "Paul",
          |         "Age": 50
          |       },
          |       {
          |         "Name": "Bob",
          |         "Age" : 30
          |       }
          |     ]
          |  }
          |]
        """.stripMargin

      assert(JsonPath.runStrict("$[0].brothers[1].Age", input) == Right(Json.fromInt(30)))
    }

    test("have a pretty rendering via Show") {
      assert(JsonPath.parse("a.b[1].d.e[*]").map(_.show) == Right("a.b[1].d.e[*]"))
    }

  }
}
