package com.github.agourlay.cornichon.steps.regular.assertStep

import munit.FunSuite

class OrderAssertionSpec extends FunSuite {

  test("LessThanAssertion valid assertion") {
    assert(LessThanAssertion(2, 3).validated.isValid)
  }

  test("LessThanAssertion invalid assertion") {
    assert(LessThanAssertion(3, 2).validated.isInvalid)
  }

  test("GreaterThanAssertion valid assertion") {
    assert(GreaterThanAssertion(3, 2).validated.isValid)
  }

  test("GreaterThanAssertion invalid assertion") {
    assert(GreaterThanAssertion(2, 3).validated.isInvalid)
  }

  test("BetweenAssertion valid assertion") {
    assert(BetweenAssertion(2, 3, 4).validated.isValid)
  }

  test("BetweenAssertion invalid assertion") {
    assert(BetweenAssertion(4, 3, 2).validated.isInvalid)
  }

}
