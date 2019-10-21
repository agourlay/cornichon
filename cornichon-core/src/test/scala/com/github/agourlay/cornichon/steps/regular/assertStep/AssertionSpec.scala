package com.github.agourlay.cornichon.steps.regular.assertStep

import utest._

object AssertionSpec extends TestSuite {

  val tests = Tests {
    test("compose with And all valid is valid") {
      val assertion = Assertion.alwaysValid and Assertion.alwaysValid
      assert(assertion.validated.isValid)
    }

    test("compose with And if on invalid then invalid") {
      val assertion = Assertion.failWith("always fail!") and Assertion.alwaysValid
      assert(!assertion.validated.isValid)
    }

    test("compose with Or valid if one valid") {
      val assertion = Assertion.failWith("always fail!") or Assertion.alwaysValid
      assert(assertion.validated.isValid)
    }

    test("compose with Or invalid if all invalid") {
      val assertion = Assertion.failWith("always fail!") and Assertion.failWith("boom!")
      assert(!assertion.validated.isValid)
    }

    test("Assertion.all all valid is valid") {
      val assertion = Assertion.all(List(Assertion.alwaysValid, Assertion.alwaysValid))
      assert(assertion.validated.isValid)
    }

    test("Assertion.all af one invalid then invalid") {
      val assertion = Assertion.all(List(Assertion.failWith("always fail!"), Assertion.alwaysValid))
      assert(!assertion.validated.isValid)
    }

    test("Assertion.any all valid is valid") {
      val assertion = Assertion.any(List(Assertion.alwaysValid, Assertion.alwaysValid))
      assert(assertion.validated.isValid)
    }

    test("Assertion.any if one valid then valid") {
      val assertion = Assertion.any(List(Assertion.alwaysValid, Assertion.failWith("always fail!")))
      assert(assertion.validated.isValid)
    }

    test("Assertion.any invalid if all invalid valid") {
      val assertion = Assertion.any(List(Assertion.failWith("boom!"), Assertion.failWith("always fail!")))
      assert(!assertion.validated.isValid)
    }
  }
}
