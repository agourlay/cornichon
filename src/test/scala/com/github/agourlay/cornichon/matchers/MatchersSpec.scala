package com.github.agourlay.cornichon.matchers

import cats.scalatest.EitherValues
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{ Matchers, WordSpec }
import com.github.agourlay.cornichon.matchers.Matchers._

class MatchersSpec extends WordSpec
    with Matchers
    with PropertyChecks
    with EitherValues {

  "Matchers" when {
    "any-integer" must {
      "correct for any int" in {
        forAll(Gen.size) { int ⇒
          anyInteger.predicate(int.toString) should be(true)
        }
      }

      "incorrect for any alphanum string" in {
        forAll(Gen.alphaNumStr) { int ⇒
          anyInteger.predicate(int.toString) should be(false)
        }
      }
    }
  }
}
