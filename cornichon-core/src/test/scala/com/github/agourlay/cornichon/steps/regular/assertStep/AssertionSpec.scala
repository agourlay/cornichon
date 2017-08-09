package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.scalatest.{ ValidatedMatchers, ValidatedValues }
import org.scalatest.{ Matchers, WordSpec }

class AssertionSpec extends WordSpec
  with Matchers
  with ValidatedMatchers
  with ValidatedValues {

  "Assertion" when {

    "compose with And" must {
      "all valid is valid" in {
        (Assertion.alwaysValid and Assertion.alwaysValid).validated should be(valid)
      }

      "if on invalid then invalid" in {
        (Assertion.failWith("always fail!") and Assertion.alwaysValid).validated should be(invalid)
      }
    }

    "compose with Or" must {
      "all valid is valid" in {
        (Assertion.failWith("always fail!") or Assertion.alwaysValid).validated should be(valid)
      }

      "if one invalid then valid" in {
        (Assertion.failWith("always fail!") or Assertion.alwaysValid).validated should be(valid)
      }
    }

    "Assertion.all" must {
      "all valid is valid" in {
        Assertion.all(List(Assertion.alwaysValid, Assertion.alwaysValid)).validated should be(valid)
      }

      "if one invalid then invalid" in {
        Assertion.all(List(Assertion.failWith("always fail!"), Assertion.alwaysValid)).validated should be(invalid)
      }
    }

    "Assertion.any" must {
      "all valid is valid" in {
        Assertion.any(List(Assertion.alwaysValid, Assertion.alwaysValid)).validated should be(valid)
      }

      "if one valid then valid" in {
        Assertion.any(List(Assertion.failWith("always fail!"), Assertion.alwaysValid)).validated should be(valid)
      }
    }
  }
}
