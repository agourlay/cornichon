package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.instances.int._
import org.scalatest.{ Matchers, WordSpec }

class OrderAssertionSpec extends WordSpec
  with Matchers {

  "OrderAssertion" must {

    "LessThenAssertion" must {
      "valid assertion" in {
        LessThanAssertion(2, 3).validated.isValid should be(true)
      }

      "invalid" in {
        LessThanAssertion(3, 2).validated.isValid should be(false)
      }
    }

    "GreaterThenAssertion" must {
      "valid assertion" in {
        GreaterThanAssertion(3, 2).validated.isValid should be(true)
      }

      "invalid" in {
        GreaterThanAssertion(2, 3).validated.isValid should be(false)
      }
    }

    "BetweenAssertion" must {
      "valid assertion" in {
        BetweenAssertion(2, 3, 4).validated.isValid should be(true)
      }

      "invalid" in {
        BetweenAssertion(4, 3, 2).validated.isValid should be(false)
      }
    }
  }
}
