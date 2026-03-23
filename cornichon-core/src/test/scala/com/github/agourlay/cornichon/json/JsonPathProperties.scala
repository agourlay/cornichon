package com.github.agourlay.cornichon.json

import io.circe.{Json, JsonObject}
import org.scalacheck.{Gen, Properties, Test}
import org.scalacheck.Prop._
import io.circe.testing.ArbitraryInstances

class JsonPathProperties extends Properties("JsonPath") with ArbitraryInstances {

  // avoid lists too long (default: 100)
  override def overrideParameters(p: Test.Parameters): Test.Parameters = super.overrideParameters(p.withMaxSize(10))

  property("select properly in any JsonObject") = {
    val targetValue = Json.fromString("target value")
    forAll { (jos: List[JsonObject]) =>
      val json = jos.foldRight(targetValue) { case (next, acc) => Json.fromJsonObject(next.add("stitch", acc)) }
      val path = Vector.fill(jos.size)(FieldSelection("stitch"))
      JsonPath(path).run(json).contains(targetValue)
    }
  }

  property("root path selects the whole document") = forAll { (jo: JsonObject) =>
    val json = Json.fromJsonObject(jo)
    JsonPath.rootPath.run(json).contains(json)
  }

  property("non-existent field returns None") = forAll { (jo: JsonObject) =>
    val json = Json.fromJsonObject(jo)
    val missingField = "this_field_does_not_exist_" + System.nanoTime()
    JsonPath(Vector(FieldSelection(missingField))).run(json).isEmpty
  }

  property("array index 0 selects first element") = forAll { (values: List[Int]) =>
    values.nonEmpty ==> {
      val json = Json.fromValues(values.map(Json.fromInt))
      val path = JsonPath(Vector(RootArrayElementSelection(0)))
      path.run(json).contains(Json.fromInt(values.head))
    }
  }

  property("array index out of bounds returns None") = forAll { (values: List[Int]) =>
    values.nonEmpty ==> {
      val json = Json.fromValues(values.map(Json.fromInt))
      val path = JsonPath(Vector(RootArrayElementSelection(values.size)))
      path.run(json).isEmpty
    }
  }

  property("wildcard projection collects all elements") = {
    val elems = List(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3))
    val json = Json.fromValues(elems)
    val path = JsonPath(Vector(RootArrayFieldProjection))
    path.run(json).contains(Json.fromValues(elems))
  }

  property("field projection on array of objects") = forAll(Gen.choose(1, 10)) { n =>
    val names = (1 to n).map(i => s"item$i").toVector
    val objects = names.map(name => Json.obj("name" -> Json.fromString(name)))
    val json = Json.obj("items" -> Json.fromValues(objects))
    val path = JsonPath(Vector(ArrayFieldProjection("items")))
    val expected = Json.fromValues(objects)
    path.run(json).contains(expected)
  }

  property("runStrict returns Left for non-existent path") = forAll { (jo: JsonObject) =>
    val json = Json.fromJsonObject(jo)
    val missingField = "missing_" + System.nanoTime()
    JsonPath(Vector(FieldSelection(missingField))).runStrict(json).isLeft
  }

  property("parse and run roundtrip for simple field") = forAll(Gen.alphaStr.filter(_.nonEmpty)) { field =>
    val json = Json.obj(field -> Json.fromString("value"))
    val parsed = JsonPath.parse(s"$$.$field")
    parsed.isRight && parsed.toOption.get.run(json).contains(Json.fromString("value"))
  }

}
