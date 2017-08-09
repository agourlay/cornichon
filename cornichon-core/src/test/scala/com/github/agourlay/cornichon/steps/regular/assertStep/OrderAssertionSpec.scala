package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.scalatest.{ ValidatedMatchers, ValidatedValues }
import cats.instances.int._
import org.scalatest.{ Matchers, WordSpec }

class OrderAssertionSpec extends WordSpec
  with Matchers
  with ValidatedMatchers
  with ValidatedValues {

  "OrderAssertion" must {

    "LessThenAssertion" must {
      "valid assertion" in {
        LessThanAssertion(2, 3).validated should be(valid)
      }

      "invalid" in {
        LessThanAssertion(3, 2).validated should be(invalid)
      }
    }

    "GreaterThenAssertion" must {
      "valid assertion" in {
        GreaterThanAssertion(3, 2).validated should be(valid)
      }

      "invalid" in {
        GreaterThanAssertion(2, 3).validated should be(invalid)
      }
    }

    "BetweenAssertion" must {
      "valid assertion" in {
        BetweenAssertion(2, 3, 4).validated should be(valid)
      }

      "invalid" in {
        BetweenAssertion(4, 3, 2).validated should be(invalid)
      }
    }
  }
}
