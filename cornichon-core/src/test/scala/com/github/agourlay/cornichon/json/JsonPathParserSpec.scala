package com.github.agourlay.cornichon.json

import cats.scalatest.EitherMatchers
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{ EitherValues, Matchers, WordSpec }
import com.github.agourlay.cornichon.json.JsonPathParserSpec._

class JsonPathParserSpec extends WordSpec
    with Matchers
    with PropertyChecks
    with EitherValues
    with EitherMatchers {

  "JsonPathParser" when {
    "parseJsonPath" must {
      "parse JsonPath containing a field without index" in {
        forAll(fieldGen) { field ⇒
          JsonPathParser.parseJsonPath(field) should beRight(List(JsonSegment(field, None)))
        }
      }

      "parse JsonPath containing a field with index" in {
        forAll(fieldGen, indiceGen) { (field, indice) ⇒
          JsonPathParser.parseJsonPath(s"$field[$indice]") should beRight(List(JsonSegment(field, Some(indice))))
        }
      }

      "parse JsonPath containing two fields without index" in {
        forAll(fieldGen, fieldGen) { (field1, field2) ⇒
          JsonPathParser.parseJsonPath(s"$field1.$field2") should beRight(List(JsonSegment(field1, None), JsonSegment(field2, None)))
        }
      }

      "parse JsonPath with key containing a dot" in {
        forAll(fieldGen, fieldGen, fieldGen) { (field1, field2, field3) ⇒
          val composedPath = s"$field1.$field2"
          val fullPath = s"`$composedPath`.$field3"
          withClue(s"fullPath was $fullPath") {
            JsonPathParser.parseJsonPath(fullPath) should beRight(List(JsonSegment(composedPath, None), JsonSegment(field3, None)))
          }
        }
      }

      "return error if it starts with '.'" in {
        forAll(fieldGen, indiceGen) { (field, indice) ⇒
          JsonPathParser.parseJsonPath(s".$field.") should be(left)
        }
      }

      "parse JsonPath containing a missing field" in {
        forAll(fieldGen, indiceGen) { (field, indice) ⇒
          JsonPathParser.parseJsonPath(s"$field.") should be(left)
        }
      }

      "parse JsonPath containing a broken index bracket" in {
        forAll(fieldGen, indiceGen) { (field, indice) ⇒
          JsonPathParser.parseJsonPath(s"$field[$indice[") should be(left)
        }
      }
    }
  }
}

object JsonPathParserSpec {
  val fieldGen = Gen.alphaStr.filter(_.nonEmpty)
  def fieldsGen(n: Int) = Gen.listOfN(n, fieldGen)

  val indiceGen = Gen.choose(0, Int.MaxValue)
}