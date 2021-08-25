package com.github.agourlay.cornichon.steps.regular.assertStep

import munit.FunSuite

class StringAssertionSpec extends FunSuite {

  test("StringContainsAssertion valid assertion") {
    assert(StringContainsAssertion("the text string", "text").validated.isValid)
  }

  test("StringContainsAssertion invalid assertion") {
    assert(StringContainsAssertion("the text string", "other").validated.isInvalid)
  }

  test("RegexAssertion valid assertion") {
    assert(RegexAssertion("the text string sample 434", "\\d+".r).validated.isValid)
  }

  test("RegexAssertion invalid assertion") {
    assert(RegexAssertion("the text string sample 434", "[A-Z]+".r).validated.isInvalid)
  }
}
