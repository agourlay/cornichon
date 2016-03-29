package com.github.agourlay.cornichon.json

import org.json4s._
import org.json4s.JsonDSL._
import org.scalatest.prop.PropertyChecks

import org.scalatest.{ Matchers, WordSpec }

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
            ("TwoLettersName", JBool(false)),
            ("Age", JInt(50)),
            ("Name", JString("John"))
          )
        )
        removeFieldsByPath(input, Seq(JsonPath.root.TwoLettersName, JsonPath.root.Name)) should be(JObject(List(("Age", JInt(50)))))
      }

      "remove only root keys" in {
        val input = ("name" → "bob") ~ ("age", 50) ~ ("brother" → (("name" → "john") ~ ("age", 40)))

        val expected = ("age", 50) ~ ("brother" → (("name" → "john") ~ ("age", 40)))

        removeFieldsByPath(input, Seq(JsonPath.root.name)) should be(expected)
      }

      "remove nested keys" in {
        val input: JValue =
          ("name" → "bob") ~
            ("age", 50) ~
            ("brother" →
              (("name" → "john") ~ ("age", 40)))

        val expected = ("name" → "bob") ~ ("age", 50) ~ ("brother" → ("age", 40))

        removeFieldsByPath(input, Seq(JsonPath.root.brother.name)) should be(expected)
      }

      //FIXME
      "remove field in each element of an array" ignore {
        val p1 = ("name" → "bob") ~ ("age", 50)
        val p2 = ("name" → "jim") ~ ("age", 40)
        val p3 = ("name" → "john") ~ ("age", 30)

        val input = JArray(List(p1, p2, p3))
        val expected = JArray(List(JObject(JField("name", "bob"), JField("name", "jim"), JField("name", "john"))))

        removeFieldsByPath(input, Seq(JsonPath.root.age)) should be(expected)
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

        println(prettyPrint(removeFieldsByPath(input, Seq(JsonPath.root.brother.name))))
        removeFieldsByPath(input, Seq(JsonPath.root.brother.name)) should be(expected)
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

        val out = parseGraphQLJson(in)

        out should be(
          JObject(List(
            "id" → JInt(1),
            "name" → JString("door"),
            "items" → JArray(List(
              JObject(List(
                "state" → JString("Open"),
                "durability" → JDecimal(BigDecimal("0.1465645654675762354763254763343243242"))
              )),
              JNull,
              JObject(List(
                "state" → JString("Open"),
                "durability" → JDecimal(BigDecimal("0.5")),
                "foo" → JNull
              ))
            ))
          ))
        )

      }
    }
  }
}
