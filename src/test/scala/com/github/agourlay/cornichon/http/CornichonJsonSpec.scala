package com.github.agourlay.cornichon.http

import org.json4s.JsonAST._
import org.scalatest.{ Matchers, WordSpec }

class CornichonJsonSpec extends WordSpec with Matchers {

  "CornichonJson" when {
    "parseJson" must {
      "parse Boolean true" in {
        CornichonJson.parseJson(true) should be(JBool.True)
      }

      "parse Boolean false" in {
        CornichonJson.parseJson(false) should be(JBool.False)
      }

      "parse Int" in {
        CornichonJson.parseJson(3) should be(JInt(3))
      }

      "parse Long" in {
        CornichonJson.parseJson(3l) should be(JLong(3L))
      }

      "parse Double" in {
        CornichonJson.parseJson(3d) should be(JDouble(3d))
      }

      "parse flat string" in {
        CornichonJson.parseJson("cornichon") should be(JString("cornichon"))
      }

      "parse data table" in {
        CornichonJson.parseJson("""
           |  Name  |   Age  | 2LettersName |
           | "John" |   50   |    false     |
           | "Bob"  |   11   |    true      |
         """) should be(JArray(List(
          JObject(List(("2LettersName", JBool(false)), ("Age", JInt(50)), ("Name", JString("John")))),
          JObject(List(("2LettersName", JBool(true)), ("Age", JInt(11)), ("Name", JString("Bob"))))
        )))
      }
    }
  }
}
