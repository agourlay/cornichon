package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.instances.int._
import utest._

object OrderAssertionSpec extends TestSuite {

  val tests = Tests {
    test("LessThenAssertion valid assertion") {
      assert(LessThanAssertion(2, 3).validated.isValid)
    }

    test("LessThenAssertion invalid assertion") {
      assert(LessThanAssertion(3, 2).validated.isInvalid)
    }

    test("GreaterThenAssertion valid assertion") {
      assert(GreaterThanAssertion(3, 2).validated.isValid)
    }

    test("GreaterThenAssertion invalid assertion") {
      assert(GreaterThanAssertion(2, 3).validated.isInvalid)
    }

    test("BetweenAssertion valid assertion") {
      assert(BetweenAssertion(2, 3, 4).validated.isValid)
    }

    test("BetweenAssertion invalid assertion") {
      assert(BetweenAssertion(4, 3, 2).validated.isInvalid)
    }
  }
}
