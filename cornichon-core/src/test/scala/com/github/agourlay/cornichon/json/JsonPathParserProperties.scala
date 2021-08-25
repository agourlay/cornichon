package com.github.agourlay.cornichon.json

import org.scalacheck.{ Gen, Properties }
import org.scalacheck.Prop._
import com.github.agourlay.cornichon.json.JsonPathParserProperties._

class JsonPathParserProperties extends Properties("JsonPathParser") {

  property("parse JsonPath containing a field without index") = {
    forAll(fieldGen) { field =>
      JsonPathParser.parseJsonPath(field) == Right(List(FieldSelection(field)))
    }
  }

  property("parse JsonPath containing a field with index") = {
    forAll(fieldGen, indexGen) { (field, index) =>
      JsonPathParser.parseJsonPath(s"$field[$index]") == Right(List(ArrayFieldSelection(field, index)))
    }
  }

  property("parse JsonPath containing two fields without index") = {
    forAll(fieldGen, fieldGen) { (field1, field2) =>
      JsonPathParser.parseJsonPath(s"$field1.$field2") == Right(List(FieldSelection(field1), FieldSelection(field2)))
    }
  }

  property("parse JsonPath containing a field with projection") = {
    forAll(fieldGen) { field =>
      JsonPathParser.parseJsonPath(s"$field[*]") == Right(List(ArrayFieldProjection(field)))
    }
  }

  property("parse JsonPath with key containing a dot without index") = {
    forAll(fieldGen, fieldGen, fieldGen) { (field1, field2, field3) =>
      val composedPath = s"$field1.$field2"
      val fullPath = s"`$composedPath`.$field3"
      JsonPathParser.parseJsonPath(fullPath) == Right(List(FieldSelection(composedPath), FieldSelection(field3)))
    }
  }

  property("parse JsonPath with key containing a dot with index") = {
    forAll(fieldGen, fieldGen, fieldGen, indexGen) { (field1, field2, field3, index) =>
      val composedPath = s"$field1.$field2"
      val fullPath = s"`$composedPath`.$field3[$index]"
      JsonPathParser.parseJsonPath(fullPath) == Right(List(FieldSelection(composedPath), ArrayFieldSelection(field3, index)))
    }
  }

  property("return error if it starts with '.'") = {
    forAll(fieldGen) { field =>
      JsonPathParser.parseJsonPath(s".$field.").isLeft
    }
  }

  property("parse JsonPath containing a missing field") = {
    forAll(fieldGen) { field =>
      JsonPathParser.parseJsonPath(s"$field.").isLeft
    }
  }

  property("parse JsonPath containing a broken index bracket") = {
    forAll(fieldGen, indexGen) { (field, index) =>
      JsonPathParser.parseJsonPath(s"$field[$index[").isLeft
    }
  }
}

object JsonPathParserProperties {
  val fieldGen = Gen.alphaStr.filter(_.trim.nonEmpty).filter(k => !JsonPathParser.notAllowedInField.exists(forbidden => k.contains(forbidden)))
  def fieldsGen(n: Int) = Gen.listOfN(n, fieldGen)

  val indexGen = Gen.choose(0, Int.MaxValue)
}