package com.github.agourlay.cornichon.json

import org.scalatest.{ Matchers, OptionValues, WordSpec }
import io.circe.Json
import cats.syntax.show._

class JsonPathSpec extends WordSpec with Matchers with OptionValues {

  "JsonPath" must {

    "non strict version returns None if the field does not exist" in {
      val input =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John"
          |}
        """.stripMargin

      JsonPath.run("Name2", input) should be(Right(None))
    }

    "select properly null field" in {
      val input =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": null
          |}
        """.stripMargin

      JsonPath.runStrict("Name", input) should be(Right(Json.Null))
    }

    "select properly String based on single field" in {
      val input =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John"
          |}
        """.stripMargin

      JsonPath.runStrict("Name", input) should be(Right(Json.fromString("John")))
    }

    "select properly Int based on single field" in {
      val input =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John"
          |}
        """.stripMargin

      JsonPath.runStrict("Age", input) should be(Right(Json.fromInt(50)))
    }

    "select properly nested field in Object" in {
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

      JsonPath.runStrict("brother.Age", input) should be(Right(Json.fromInt(50)))
    }

    "select properly nested field in Array" in {
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

      JsonPath.runStrict("brothers[1].Age", input) should be(Right(Json.fromInt(30)))
    }

    "select properly nested fields projected in Array" in {
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

      JsonPath.runStrict("brothers[*].Age", input) should be(Right(Json.arr(Json.fromInt(50), Json.fromInt(30))))
    }

    "select properly nested fields projected in Array with a single value" in {
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
          |   }
          | ]
          |}
        """.stripMargin

      JsonPath.runStrict("brothers[*].Age", input) should be(Right(Json.arr(Json.fromInt(50))))
    }

    "return empty array if projection on Array but nested field does not exist" in {
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
          |   }
          | ]
          |}
        """.stripMargin

      JsonPath.runStrict("brothers[*].age", input) should be(Right(Json.fromValues(Nil)))
    }

    "return empty array if projection on empty Array but nested field does not exist" in {
      val input =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John",
          | "brothers": []
          |}
        """.stripMargin

      JsonPath.runStrict("brothers[*].age", input) should be(Right(Json.fromValues(Nil)))
    }

    "return None (nonStrict) if the array projected does not exist" in {
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
          |   }
          | ]
          |}
        """.stripMargin

      JsonPath.run("sisters[*].age", input) should be(Right(None))
    }

    "select properly doubly nested fields projected in Array" in {
      val input =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John",
          | "Brothers": [
          |   {
          |     "Name" : "Paul",
          |     "Age": 50,
          |     "Hobbies" : [
          |       {
          |         "Name" : "Karate",
          |         "Level" : "Good"
          |       },{
          |         "Name" : "Football",
          |         "Level" : "Beginner"
          |       }
          |     ]
          |   },
          |   {
          |     "Name": "Bob",
          |     "Age" : 30,
          |     "Hobbies" : [
          |       {
          |         "Name" : "Diving",
          |         "Level" : "Good"
          |       },{
          |         "Name" : "Reading",
          |         "Level" : "Beginner"
          |       }
          |     ]
          |   }
          | ]
          |}
        """.stripMargin

      JsonPath.runStrict("Brothers[*].Hobbies[*].Name", input) should be(Right(Json.arr(
        Json.fromString("Karate"), Json.fromString("Football"), Json.fromString("Diving"), Json.fromString("Reading")
      )))
    }

    "select properly element of a root Array" in {
      val input =
        """
          |[{
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
          |}]
        """.stripMargin

      JsonPath.runStrict("$[0].brothers[1].Age", input) should be(Right(Json.fromInt(30)))
    }
    "have a pretty rendering via Show" in {
      JsonPath.parse("a.b[1].d.e[*]").map(_.show) should be(Right("$.a.b[1].d.e[*]"))
    }
  }
}
