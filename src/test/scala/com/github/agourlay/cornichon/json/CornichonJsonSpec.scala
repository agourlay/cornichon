package com.github.agourlay.cornichon.json

import org.json4s._
import org.json4s.JsonDSL._

import org.scalatest.{ Matchers, WordSpec }

class CornichonJsonSpec extends WordSpec with Matchers with CornichonJson {

  "CornichonJson" when {
    "parseJson" must {
      "parse Boolean true" in {
        parseJson(true) should be(JBool.True)
      }

      "parse Boolean false" in {
        parseJson(false) should be(JBool.False)
      }

      "parse Int" in {
        parseJson(3) should be(JInt(3))
      }

      "parse Long" in {
        parseJson(3l) should be(JLong(3L))
      }

      "parse Double" in {
        parseJson(3d) should be(JDouble(3d))
      }

      "parse BigDecimal" in {
        parseJson(BigDecimal(3.6d)) should be(JDecimal(3.6d))
      }

      "parse flat string" in {
        parseJson("cornichon") should be(JString("cornichon"))
      }

      "parse JSON object string" in {
        parseJson("""{"name":"cornichon"}""") should be(JObject(JField("name", JString("cornichon"))))
      }

      "parse JSON Array string" in {
        parseJson(
          """
           [
            {"name":"cornichon"},
            {"name":"scala"}
           ]
           """
        ) should be(JArray(List(
            JObject(List(("name", JString("cornichon")))),
            JObject(List(("name", JString("scala"))))
          )))
      }

      "parse data table" in {
        parseJson("""
           |  Name  |   Age  | 2LettersName |
           | "John" |   50   |    false     |
           | "Bob"  |   11   |    true      |
         """) should be(JArray(List(
          JObject(List(("2LettersName", JBool.False), ("Age", JInt(50)), ("Name", JString("John")))),
          JObject(List(("2LettersName", JBool.True), ("Age", JInt(11)), ("Name", JString("Bob"))))
        )))
      }
    }

    "filterJsonRootKeys" must {
      "remove root key" in {
        val input = JObject(
          List(
            ("2LettersName", JBool(false)),
            ("Age", JInt(50)),
            ("Name", JString("John"))
          )
        )
        filterJsonRootKeys(input, Seq("2LettersName", "Name")) should be(JObject(List(("Age", JInt(50)))))
      }

      "remove only root keys" in {
        val input = ("name" → "bob") ~ ("age", 50) ~ ("brother" → ("name" → "john") ~ ("age", 40))

        val expected = ("age", 50) ~ ("brother" → ("name" → "john") ~ ("age", 40))

        filterJsonRootKeys(input, Seq("name")) should be(expected)
      }
    }
  }
}
