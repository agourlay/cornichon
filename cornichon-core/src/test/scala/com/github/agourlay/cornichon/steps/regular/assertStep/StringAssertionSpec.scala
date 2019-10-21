package com.github.agourlay.cornichon.steps.regular.assertStep

import utest._

object StringAssertionSpec extends TestSuite {

  val tests = Tests {
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
}
