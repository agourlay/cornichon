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

      JsonPath.parse("Name").run(input) should beRight(Json.fromString("John"))
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

      JsonPath.parse("Age").run(input) should beRight(Json.fromInt(50))
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

      JsonPath.parse("brother.Age").run(input) should beRight(Json.fromInt(50))
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

      JsonPath.parse("brothers[1].Age").run(input) should beRight(Json.fromInt(30))
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

      JsonPath.parse("$[0].brothers[1].Age").run(input) should beRight(Json.fromInt(30))
    }
  }
}
