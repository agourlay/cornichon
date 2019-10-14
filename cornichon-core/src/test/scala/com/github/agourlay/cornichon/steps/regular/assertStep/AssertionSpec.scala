package com.github.agourlay.cornichon.steps.regular.assertStep

import org.scalatest.{ Matchers, WordSpec }

class AssertionSpec extends WordSpec with Matchers {

  "Assertion" when {

    "compose with And" must {
      "all valid is valid" in {
        (Assertion.alwaysValid and Assertion.alwaysValid).validated.isValid should be(true)
      }

      "if on invalid then invalid" in {
        (Assertion.failWith("always fail!") and Assertion.alwaysValid).validated.isValid should be(false)
      }
    }

    "compose with Or" must {
      "all valid is valid" in {
        (Assertion.failWith("always fail!") or Assertion.alwaysValid).validated.isValid should be(true)
      }

      "if one invalid then valid" in {
        (Assertion.failWith("always fail!") or Assertion.alwaysValid).validated.isValid should be(true)
      }
    }

    "Assertion.all" must {
      "all valid is valid" in {
        Assertion.all(List(Assertion.alwaysValid, Assertion.alwaysValid)).validated.isValid should be(true)
      }

      "if one invalid then invalid" in {
        Assertion.all(List(Assertion.failWith("always fail!"), Assertion.alwaysValid)).validated.isValid should be(false)
      }
    }

    "Assertion.any" must {
      "all valid is valid" in {
        Assertion.any(List(Assertion.alwaysValid, Assertion.alwaysValid)).validated.isValid should be(true)
      }

      "if one valid then valid" in {
        Assertion.any(List(Assertion.failWith("always fail!"), Assertion.alwaysValid)).validated.isValid should be(true)
      }
    }
  }
}
