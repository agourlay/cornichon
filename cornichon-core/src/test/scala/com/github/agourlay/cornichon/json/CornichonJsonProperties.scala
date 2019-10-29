package com.github.agourlay.cornichon.json

import io.circe.{ Json, JsonObject }
import io.circe.testing.ArbitraryInstances
import cats.instances.string._
import cats.instances.boolean._
import cats.instances.int._
import cats.instances.long._
import cats.instances.double._
import cats.instances.bigDecimal._
import org.scalacheck.{ Gen, Properties }
import org.scalacheck.Prop._
import org.typelevel.claimant.Claim

class CornichonJsonProperties extends Properties("CornichonJson") with CornichonJson with ArbitraryInstances {

  property("parseJson Boolean") =
    forAll { bool: Boolean =>
      Claim(parseDslJson(bool) == Right(Json.fromBoolean(bool)))
    }

  property("parseJson Int") =
    forAll { int: Int =>
      Claim(parseDslJson(int) == Right(Json.fromInt(int)))
    }

  property("parseJson alphanumeric String") =
    forAll(Gen.alphaNumStr) { s: String =>
      Claim(parseDslJson(s) == Right(Json.fromString(s)))
    }

  property("parseJson alphanumeric String conserves spaces") =
    forAll(Gen.alphaNumStr) { s: String =>
      val decorated = s"  $s  "
      Claim(parseDslJson(decorated) == Right(Json.fromString(decorated)))
    }

  property("parseJson Long") =
    forAll { long: Long =>
      Claim(parseDslJson(long) == Right(Json.fromLong(long)))
    }

  property("parseJson Double") =
    forAll { double: Double =>
      Claim(parseDslJson(double) == Right(Json.fromDoubleOrNull(double)))
    }

  property("parseJson BigDecimal") =
    forAll { bigDec: BigDecimal =>
      Claim(parseDslJson(bigDec) == Right(Json.fromBigDecimal(bigDec)))
    }

  //  property("parse any Circe Json") = {
  //    forAll { json: Json =>
  //      parseDslJson(json.spaces2) == Right(json)
  //    }
  //  }

  property("findAllContainingValue find key in any JsonObject") = {
    val targetValue = Json.fromString("target value")
    forAll { jos: List[JsonObject] =>

      val json = jos.foldRight(targetValue) { case (next, acc) => Json.fromJsonObject(next.add("stitch", acc)) }

      val path = findAllPathWithValue("target value" :: Nil, json).head
      Claim {
        path.run(json).contains(targetValue)
      }
    }
  }
}
