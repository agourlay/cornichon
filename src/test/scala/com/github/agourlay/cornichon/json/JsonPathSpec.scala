package com.github.agourlay.cornichon.json

import org.scalatest.prop.PropertyChecks
import org.scalatest.{ Matchers, WordSpec }
import cats.data.Xor._
import io.circe.Json

class JsonPathSpec extends WordSpec with Matchers with PropertyChecks {

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

      JsonPath.parse("Name").run(input) should be(right(Json.fromString("John")))
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

      JsonPath.parse("Age").run(input) should be(right(Json.fromInt(50)))
    }
  }
}
