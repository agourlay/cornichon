package com.github.agourlay.cornichon.matchers

import cats.scalatest.EitherValues
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest._
import com.github.agourlay.cornichon.matchers.Matchers._
import io.circe.Json

class MatchersSpec extends WordSpec
  with Matchers
  with PropertyChecks
  with EitherValues {

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
  }
}
