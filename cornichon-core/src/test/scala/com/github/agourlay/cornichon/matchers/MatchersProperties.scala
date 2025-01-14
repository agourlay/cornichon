package com.github.agourlay.cornichon.matchers

import cats.Parallel
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import java.time.Instant
import java.time.format.DateTimeFormatter
import com.github.agourlay.cornichon.matchers.Matchers._
import io.circe.{ Json, JsonObject }
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

  property("any-integer correct for any int") =
    forAll(Gen.size) { int =>
      anyInteger.predicate(Json.fromInt(int))
    }

  property("any-integer incorrect for any alphanum string") =
    forAll(Gen.alphaNumStr) { alphanum =>
      !anyInteger.predicate(Json.fromString(alphanum))
    }

  property("any-positive-integer correct for any positive int") =
    forAll(Gen.choose(1, Int.MaxValue)) { int =>
      anyPositiveInteger.predicate(Json.fromInt(int))
    }

  property("any-positive-integer incorrect for any alphanum string") =
    forAll(Gen.alphaNumStr) { alphanum =>
      !anyPositiveInteger.predicate(Json.fromString(alphanum))
    }

  property("any-negative-integer correct for any negative int") =
    forAll(Gen.negNum[Int]) { int =>
      anyNegativeInteger.predicate(Json.fromInt(int))
    }

  property("any-negative-integer incorrect for any alphanum string") =
    forAll(Gen.alphaNumStr) { alphanum =>
      !anyNegativeInteger.predicate(Json.fromString(alphanum))
    }

  property("any-uuid correct for any valid UUID") =
    forAll(Gen.uuid) { uuid =>
      anyUUID.predicate(Json.fromString(uuid.toString))
    }

  property("any-uuid incorrect for any alphanum string") =
    forAll(Gen.alphaNumStr) { alphanum =>
      !anyUUID.predicate(Json.fromString(alphanum))
    }

  property("any-date-time correct for all ISO-compliant values, including Y10K+ dates") =
    forAll(instantGen) { instant =>
      anyDateTime.predicate(Json.fromString(DateTimeFormatter.ISO_INSTANT.format(instant)))
    }

  property("any-date-time correct in parallel") = {
    forAll(reasonablyRandomInstantGen) { instant =>
      val booleans = 1.to(64).iterator.map { _ =>
        IO.delay {
          anyDateTime.predicate(Json.fromString(DateTimeFormatter.ISO_INSTANT.format(instant)))
        }
      }.toList

      val res = Parallel.parSequence(booleans).unsafeRunSync().foldLeft(List.empty[Boolean]) { case (acc, e) => e :: acc }

      res.forall(_ == true)
    }
  }

  property("any-object correct for any JsonObject") =
    forAll { (jsonObj: JsonObject) =>
      anyObject.predicate(Json.fromJsonObject(jsonObj))
    }

  property("any-string correct for any asciiPrintableStr") =
    forAll(Gen.asciiPrintableStr) { asciiPrintableStr =>
      anyString.predicate(Json.fromString(asciiPrintableStr))
    }
}
