package com.github.agourlay.cornichon.matchers

import java.time.Instant
import java.time.format.DateTimeFormatter

import cats.scalatest.EitherValues
import com.github.agourlay.cornichon.matchers.Matchers._
import io.circe.Json
import org.scalacheck.{ Arbitrary, Gen }
import org.scalatest._
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class MatchersSpec extends WordSpec
  with Matchers
  with ScalaCheckPropertyChecks
  with EitherValues {

  import MatchersSpec._

  "Matchers" when {
    "any-integer" must {
      "correct for any int" in {
        forAll(Gen.size) { int ⇒
          anyInteger.predicate(Json.fromInt(int)) should be(true)
        }
      }

      "incorrect for any alphanum string" in {
        forAll(Gen.alphaNumStr) { alphanum ⇒
          anyInteger.predicate(Json.fromString(alphanum)) should be(false)
        }
      }
    }

    "any-positive-integer" must {
      "correct for any positive int" in {
        forAll(Gen.posNum[Int]) { int ⇒
          anyPositiveInteger.predicate(Json.fromInt(int)) should be(true)
        }
      }

      "incorrect for any alphanum string" in {
        forAll(Gen.alphaNumStr) { alphanum ⇒
          anyPositiveInteger.predicate(Json.fromString(alphanum)) should be(false)
        }
      }
    }

    "any-negative-integer" must {
      "correct for any negative int" in {
        forAll(Gen.negNum[Int]) { int ⇒
          anyNegativeInteger.predicate(Json.fromInt(int)) should be(true)
        }
      }

      "incorrect for any alphanum string" in {
        forAll(Gen.alphaNumStr) { alphanum ⇒
          anyNegativeInteger.predicate(Json.fromString(alphanum)) should be(false)
        }
      }
    }

    "any-uuid" must {
      "correct for any valid UUID" in {
        forAll(Gen.uuid) { uuid ⇒
          anyUUID.predicate(Json.fromString(uuid.toString)) should be(true)
        }
      }

      "incorrect for any alphanum string" in {
        forAll(Gen.alphaNumStr) { alphanum ⇒
          anyUUID.predicate(Json.fromString(alphanum)) should be(false)
        }
      }
    }

    "any-date-time" must {
      "correct for all ISO-compliant values, including Y10K+ dates" in {
        forAll(instantGen) { instant ⇒
          anyDateTime.predicate(Json.fromString(DateTimeFormatter.ISO_INSTANT.format(instant))) should be(true)
        }
      }

      "correct in parallel" in {
        1.to(64)
          .map(_ ⇒ reasonablyRandomInstantGen.sample)
          .par
          .foreach { instant ⇒
            anyDateTime.predicate(Json.fromString(DateTimeFormatter.ISO_INSTANT.format(instant.get))) should be(true)
          }
      }
    }
  }
}

object MatchersSpec {
  val reasonablyRandomInstantGen: Gen[Instant] = for {
    randomOffset ← Arbitrary.arbLong.arbitrary
  } yield Instant.now().plusMillis(randomOffset % 1000000000000L)

  val instantGen: Gen[Instant] = for {
    randomOffset ← Arbitrary.arbLong.arbitrary
  } yield Instant.now().plusMillis(randomOffset)
}
