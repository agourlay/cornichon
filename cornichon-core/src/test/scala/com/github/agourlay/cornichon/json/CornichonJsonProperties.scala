package com.github.agourlay.cornichon.json

import io.circe.{ Json, JsonObject }
import io.circe.testing.ArbitraryInstances
import org.scalacheck.{ Gen, Properties, Test }
import org.scalacheck.Prop._

class CornichonJsonProperties extends Properties("CornichonJson") with CornichonJson with ArbitraryInstances {

  // avoid lists too long (default: 100)
  override def overrideParameters(p: Test.Parameters): Test.Parameters = super.overrideParameters(p.withMaxSize(10))

  property("parseJson Boolean") =
    forAll { bool: Boolean =>
      parseDslJson(bool) == Right(Json.fromBoolean(bool))
    }

  property("parseJson Int") =
    forAll { int: Int =>
      parseDslJson(int) == Right(Json.fromInt(int))
    }

  property("parseJson alphanumeric String") =
    forAll(Gen.alphaNumStr) { s: String =>
      val parsed = parseDslJson(s)
      val expectedQuotedParsed = parsed.map(_.spaces2).getOrElse("")
      parsed == Right(Json.fromString(s)) && expectedQuotedParsed == s""""$s""""
    }

  property("parseJson alphanumeric String conserves spaces") =
    forAll(Gen.alphaNumStr) { s: String =>
      val decorated = s"  $s  "
      parseDslJson(decorated) == Right(Json.fromString(decorated))
    }

  property("parseJson Long") =
    forAll { long: Long =>
      parseDslJson(long) == Right(Json.fromLong(long))
    }

  property("parseJson Double") =
    forAll { double: Double =>
      parseDslJson(double) == Right(Json.fromDoubleOrNull(double))
    }

  property("parseJson BigDecimal") =
    forAll { bigDec: BigDecimal =>
      parseDslJson(bigDec) == Right(Json.fromBigDecimal(bigDec))
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
      val (value, path) = findAllPathWithValue(Set("target value"), json).head
      value == "target value" && path.run(json).contains(targetValue)
    }
  }

  property("whitelisting on identical JSON has no effect") = {
    forAll { json: Json =>
      whitelistingValue(json, json) == Right(json)
    }
  }

  property("whitelisting improper subset with single added key") = {
    forAll { jsonObj: JsonObject =>
      val modifiedObj = jsonObj.add("extraKey", Json.fromString("extraValue"))
      val json = Json.fromJsonObject(jsonObj)
      val modifiedJson = Json.fromJsonObject(modifiedObj)
      whitelistingValue(json, modifiedJson) == Right(modifiedJson) &&
        whitelistingValue(modifiedJson, json) == Left(WhitelistingError(Seq("/extraKey"), json))
    }
  }

  property("whitelisting arrays with single missing key on each element") = {
    forAll { jos: List[JsonObject] =>
      // ==> did not work
      if (jos.isEmpty) {
        true
      } else {
        val modifiedList = jos.map(_.add("extraKey", Json.fromString("extraValue"))).map(Json.fromJsonObject)
        val json = Json.fromValues(jos.map(Json.fromJsonObject))
        val modifiedJson = Json.fromValues(modifiedList)
        val errors = modifiedList.iterator.zipWithIndex.map { case (_, i) => s"/$i/extraKey" }.toList
        whitelistingValue(json, modifiedJson) == Right(modifiedJson) &&
          whitelistingValue(modifiedJson, json) == Left(WhitelistingError(errors, json))
      }
    }
  }

  property("whitelisting on JSON Object with improper nested path") = {
    val targetValue = Json.fromString("target value")
    forAll { jos: List[JsonObject] =>
      val json1 = jos.foldRight(targetValue) { case (next, acc) => Json.fromJsonObject(next.add("stitch1", acc)) }
      val json2 = jos.foldRight(targetValue) { case (next, acc) => Json.fromJsonObject(next.add("stitch2", acc)) }
      // ==> did not work
      if (jos.isEmpty)
        true
      else
        whitelistingValue(json1, json2) == Left(WhitelistingError("/stitch1" :: Nil, json2)) &&
          whitelistingValue(json2, json1) == Left(WhitelistingError("/stitch2" :: Nil, json1))
    }
  }
}
