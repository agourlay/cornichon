package com.github.agourlay.cornichon.json

import org.json4s._
import org.json4s.JsonDSL._
import org.scalatest.prop.PropertyChecks

import org.scalatest.{ Ignore, Matchers, WordSpec }

class CornichonJsonSpec extends WordSpec with Matchers with PropertyChecks with CornichonJson {

  "CornichonJson" when {
    "parseJson" must {
      "parse Boolean" in {
        forAll { bool: Boolean ⇒
          parseJson(bool) should be(JBool(bool))
        }
      }

      "parse Int" in {
        forAll { int: Int ⇒
          parseJson(int) should be(JInt(int))
        }
      }

      "parse Long" in {
        forAll { long: Long ⇒
          parseJson(long) should be(JLong(long))
        }
      }

      "parse Double" in {
        forAll { double: Double ⇒
          parseJson(double) should be(JDouble(double))
        }
      }

      "parse BigDecimal" in {
        forAll { bigDec: BigDecimal ⇒
          parseJson(bigDec) should be(JDecimal(bigDec))
        }
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

    "removeFieldsByPath" must {
      "remove root key" in {
        val input = JObject(
          List(
            ("2LettersName", JBool(false)),
            ("Age", JInt(50)),
            ("Name", JString("John"))
          )
        )
        removeFieldsByPath(input, Seq("2LettersName", "Name")) should be(JObject(List(("Age", JInt(50)))))
      }

      "remove only root keys" in {
        val input = ("name" → "bob") ~ ("age", 50) ~ ("brother" → (("name" → "john") ~ ("age", 40)))

        val expected = ("age", 50) ~ ("brother" → (("name" → "john") ~ ("age", 40)))

        removeFieldsByPath(input, Seq("name")) should be(expected)
      }

      "remove nested keys" in {
        val input: JValue =
          ("name" → "bob") ~
            ("age", 50) ~
            ("brother" →
              (("name" → "john") ~ ("age", 40)))

        val expected = ("name" → "bob") ~ ("age", 50) ~ ("brother" → ("age", 40))

        removeFieldsByPath(input, Seq("brother.name")) should be(expected)
      }

      //FIXME
      "do not trip on duplicate" ignore {
        val input: JValue =
          ("name" → "bob") ~
            ("age", 50) ~
            ("brother" →
              (("name" → "john") ~ ("age", 40))) ~
              ("friend" →
                (("name" → "john") ~ ("age", 30)))

        val expected = ("name" → "bob") ~ ("age", 50) ~ ("brother" → ("age", 40)) ~ ("friend" → (("name" → "john") ~ ("age", 30)))

        println(prettyPrint(removeFieldsByPath(input, Seq("brother.name"))))
        removeFieldsByPath(input, Seq("brother.name")) should be(expected)
      }
    }
  }
}
