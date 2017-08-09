package com.github.agourlay.cornichon.json

import org.scalatest.prop.PropertyChecks
import org.scalatest.{ Matchers, WordSpec }
import cats.scalatest.{ EitherMatchers, EitherValues }
import io.circe.Json

class JsonPathSpec extends WordSpec
  with Matchers
  with PropertyChecks
  with EitherValues
  with EitherMatchers {

  "JsonPath" must {

    "select properly String based on single field" in {
      val input =
        """
          |{
          |"2LettersName" : false,
          | "Age": 50,
          | "Name": "John"
          |}
        """.stripMargin

      JsonPath.run("Name", input) should beRight(Json.fromString("John"))
    }

    "select properly Int based on single field" in {
      val input =
        """
          |{
          |"2LettersName" : false,
          | "Age": 50,
          | "Name": "John"
          |}
        """.stripMargin

      JsonPath.run("Age", input) should beRight(Json.fromInt(50))
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

      JsonPath.run("brother.Age", input) should beRight(Json.fromInt(50))
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

      JsonPath.run("brothers[1].Age", input) should beRight(Json.fromInt(30))
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

      JsonPath.run("brothers[*].Age", input) should beRight(Json.arr(Json.fromInt(50), Json.fromInt(30)))
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

      JsonPath.run("Brothers[*].Hobbies[*].Name", input) should beRight(Json.arr(
        Json.fromString("Karate"), Json.fromString("Football"), Json.fromString("Diving"), Json.fromString("Reading")
      ))
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

      JsonPath.run("$[0].brothers[1].Age", input) should beRight(Json.fromInt(30))
    }
  }
}
