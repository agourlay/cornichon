package com.github.agourlay.cornichon.matchers

import cats.Parallel
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import java.time.Instant
import java.time.format.DateTimeFormatter
import com.github.agourlay.cornichon.matchers.Matchers._
import io.circe.{Json, JsonObject}
import io.circe.testing.ArbitraryInstances
import org.scalacheck._
import org.scalacheck.Prop._

object MatchersProperties extends Properties("Matchers") with ArbitraryInstances {

  val reasonablyRandomInstantGen: Gen[Instant] = for {
    randomOffset <- Arbitrary.arbLong.arbitrary
  } yield Instant.now().plusMillis(randomOffset % 1000000000000L)

  val instantGen: Gen[Instant] = for {
    randomOffset <- Arbitrary.arbLong.arbitrary
  } yield Instant.now().plusMillis(randomOffset)

  property("any-number correct for any int") = forAll(Gen.size) { int =>
    anyNumber.predicate(Json.fromInt(int))
  }

  property("any-number correct for any double") = forAll(Arbitrary.arbDouble.arbitrary) { double =>
    anyNumber.predicate(Json.fromDoubleOrNull(double))
  }

  property("any-number incorrect for any alphanum string") = forAll(Gen.alphaNumStr) { alphanum =>
    !anyNumber.predicate(Json.fromString(alphanum))
  }

  property("any-number incorrect for boolean") = forAll(Arbitrary.arbBool.arbitrary) { bool =>
    !anyNumber.predicate(Json.fromBoolean(bool))
  }

  property("any-number incorrect for null") = !anyNumber.predicate(Json.Null)

  property("any-integer correct for any int") = forAll(Gen.size) { int =>
    anyInteger.predicate(Json.fromInt(int))
  }

  property("any-integer incorrect for any double with fractional part") = forAll(Gen.choose(0.01, 1000.0)) { double =>
    !anyInteger.predicate(Json.fromDoubleOrNull(double))
  }

  property("any-integer incorrect for any alphanum string") = forAll(Gen.alphaNumStr) { alphanum =>
    !anyInteger.predicate(Json.fromString(alphanum))
  }

  property("any-positive-integer correct for any positive int") = forAll(Gen.choose(1, Int.MaxValue)) { int =>
    anyPositiveInteger.predicate(Json.fromInt(int))
  }

  property("any-positive-integer incorrect for zero") = !anyPositiveInteger.predicate(Json.fromInt(0))

  property("any-positive-integer incorrect for negative") = forAll(Gen.negNum[Int]) { int =>
    !anyPositiveInteger.predicate(Json.fromInt(int))
  }

  property("any-positive-integer incorrect for any alphanum string") = forAll(Gen.alphaNumStr) { alphanum =>
    !anyPositiveInteger.predicate(Json.fromString(alphanum))
  }

  property("any-negative-integer correct for any negative int") = forAll(Gen.negNum[Int]) { int =>
    anyNegativeInteger.predicate(Json.fromInt(int))
  }

  property("any-negative-integer incorrect for zero") = !anyNegativeInteger.predicate(Json.fromInt(0))

  property("any-negative-integer incorrect for positive") = forAll(Gen.choose(1, Int.MaxValue)) { int =>
    !anyNegativeInteger.predicate(Json.fromInt(int))
  }

  property("any-negative-integer incorrect for any alphanum string") = forAll(Gen.alphaNumStr) { alphanum =>
    !anyNegativeInteger.predicate(Json.fromString(alphanum))
  }

  property("any-uuid correct for any valid UUID") = forAll(Gen.uuid) { uuid =>
    anyUUID.predicate(Json.fromString(uuid.toString))
  }

  property("any-uuid incorrect for any alphanum string") = forAll(Gen.alphaNumStr) { alphanum =>
    !anyUUID.predicate(Json.fromString(alphanum))
  }

  property("any-date-time correct for all ISO-compliant values, including Y10K+ dates") = forAll(instantGen) { instant =>
    anyDateTime.predicate(Json.fromString(DateTimeFormatter.ISO_INSTANT.format(instant)))
  }

  property("any-date-time correct in parallel") = forAll(reasonablyRandomInstantGen) { instant =>
    val booleans = 1
      .to(64)
      .iterator
      .map { _ =>
        IO.delay {
          anyDateTime.predicate(Json.fromString(DateTimeFormatter.ISO_INSTANT.format(instant)))
        }
      }
      .toList

    val res = Parallel.parSequence(booleans).unsafeRunSync().foldLeft(List.empty[Boolean]) { case (acc, e) => e :: acc }

    res.forall(_ == true)
  }

  property("any-object correct for any JsonObject") = forAll { (jsonObj: JsonObject) =>
    anyObject.predicate(Json.fromJsonObject(jsonObj))
  }

  property("any-object incorrect for string") = forAll(Gen.alphaStr) { s =>
    !anyObject.predicate(Json.fromString(s))
  }

  property("any-object incorrect for number") = forAll(Gen.choose(-1000, 1000)) { n =>
    !anyObject.predicate(Json.fromInt(n))
  }

  property("any-array correct for any array") = forAll(Gen.listOf(Gen.choose(0, 100))) { ints =>
    anyArray.predicate(Json.fromValues(ints.map(Json.fromInt)))
  }

  property("any-array correct for empty array") = anyArray.predicate(Json.fromValues(Nil))

  property("any-array incorrect for string") = forAll(Gen.alphaStr) { s =>
    !anyArray.predicate(Json.fromString(s))
  }

  property("any-alphanum-string correct for any non-empty alphanumeric string") = forAll(Gen.alphaNumStr.filter(_.nonEmpty)) { alphanum =>
    anyAlphaNum.predicate(Json.fromString(alphanum))
  }

  property("any-alphanum-string incorrect for empty string") = !anyAlphaNum.predicate(Json.fromString(""))

  property("any-alphanum-string incorrect for string with spaces") = !anyAlphaNum.predicate(Json.fromString("has space"))

  property("any-string correct for any asciiPrintableStr") = forAll(Gen.asciiPrintableStr) { asciiPrintableStr =>
    anyString.predicate(Json.fromString(asciiPrintableStr))
  }

  property("any-string incorrect for number") = forAll(Gen.choose(-1000, 1000)) { n =>
    !anyString.predicate(Json.fromInt(n))
  }

  property("any-boolean correct for true and false") = forAll(Arbitrary.arbBool.arbitrary) { bool =>
    anyBoolean.predicate(Json.fromBoolean(bool))
  }

  property("any-boolean incorrect for string") = forAll(Gen.alphaStr) { s =>
    !anyBoolean.predicate(Json.fromString(s))
  }

  property("is-present correct for non-null values") = forAll(Gen.alphaStr) { s =>
    isPresent.predicate(Json.fromString(s))
  }

  property("is-present incorrect for null") = !isPresent.predicate(Json.Null)

  property("is-null correct for null") = isNull.predicate(Json.Null)

  property("is-null incorrect for non-null") = forAll(Gen.alphaStr) { s =>
    !isNull.predicate(Json.fromString(s))
  }

  // Overlap properties: a positive integer is also an integer and a number
  property("any positive integer also matches any-integer") = forAll(Gen.choose(1, Int.MaxValue)) { int =>
    val json = Json.fromInt(int)
    anyPositiveInteger.predicate(json) && anyInteger.predicate(json) && anyNumber.predicate(json)
  }

  property("any negative integer also matches any-integer") = forAll(Gen.negNum[Int]) { int =>
    val json = Json.fromInt(int)
    anyNegativeInteger.predicate(json) && anyInteger.predicate(json) && anyNumber.predicate(json)
  }

}
