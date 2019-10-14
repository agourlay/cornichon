package com.github.agourlay.cornichon.json

import io.circe.{ Json, JsonObject }
import org.scalacheck.Properties
import org.scalacheck.Prop._
import org.typelevel.claimant.Claim
import io.circe.testing.ArbitraryInstances

class JsonPathProperties extends Properties("JsonPath") with ArbitraryInstances {

  property("select properly in any JsonObject") = {
    val targetValue = Json.fromString("target value")
    forAll { jos: List[JsonObject] ⇒

      val json = jos.foldRight(targetValue) { case (next, acc) ⇒ Json.fromJsonObject(next.add("stitch", acc)) }

      val path = List.fill(jos.size)(FieldSelection("stitch"))
      Claim {
        JsonPath(path).run(json).contains(targetValue)
      }
    }
  }
}
