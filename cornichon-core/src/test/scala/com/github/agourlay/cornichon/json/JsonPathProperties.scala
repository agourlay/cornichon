package com.github.agourlay.cornichon.json

import io.circe.{ Json, JsonObject }
import org.scalacheck.{ Properties, Test }
import org.scalacheck.Prop._
import io.circe.testing.ArbitraryInstances

class JsonPathProperties extends Properties("JsonPath") with ArbitraryInstances {

  // avoid lists too long (default: 100)
  override def overrideParameters(p: Test.Parameters): Test.Parameters = super.overrideParameters(p.withMaxSize(10))

  property("select properly in any JsonObject") = {
    val targetValue = Json.fromString("target value")
    forAll { jos: List[JsonObject] =>
      val json = jos.foldRight(targetValue) { case (next, acc) => Json.fromJsonObject(next.add("stitch", acc)) }
      val path = Vector.fill(jos.size)(FieldSelection("stitch"))
      JsonPath(path).run(json).contains(targetValue)
    }
  }
}
