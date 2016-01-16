package com.github.agourlay.cornichon.json

import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{ Matchers, WordSpec }

import JsonPathParserSpec._

class JsonPathParserSpec extends WordSpec with Matchers with PropertyChecks {

  "JsonPathParser" when {
    "parseJsonPath" must {
      "parse JsonPath containing a field without index" in {
        forAll(fieldGen) { field ⇒
          JsonPathParser.parseJsonPath(field) should be(List(JsonSegment(field, None)))
        }
      }

      "parse JsonPath containing a field with index" in {
        forAll(fieldGen, indiceGen) { (field, indice) ⇒
          JsonPathParser.parseJsonPath(s"$field[$indice]") should be(List(JsonSegment(field, Some(indice))))
        }
      }

      "parse JsonPath containing two fields without index" in {
        forAll(fieldGen, fieldGen) { (field1, field2) ⇒
          JsonPathParser.parseJsonPath(s"$field1.$field2") should be(List(JsonSegment(field1, None), JsonSegment(field2, None)))
        }
      }

      "return error if it starts with '.'" in {
        forAll(fieldGen, indiceGen) { (field, indice) ⇒
          intercept[JsonPathParsingError] {
            JsonPathParser.parseJsonPath(s".$field.")
          }
        }
      }

      "parse JsonPath containing a missing field" in {
        forAll(fieldGen, indiceGen) { (field, indice) ⇒
          intercept[JsonPathParsingError] {
            JsonPathParser.parseJsonPath(s"$field.")
          }
        }
      }

      "parse JsonPath containing a broken index bracket" in {
        forAll(fieldGen, indiceGen) { (field, indice) ⇒
          intercept[JsonPathParsingError] {
            JsonPathParser.parseJsonPath(s"$field[$indice[")
          }
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