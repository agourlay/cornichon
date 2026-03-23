package com.github.agourlay.cornichon.steps.regular.assertStep

import munit.FunSuite

class AssertionSpec extends FunSuite {

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
    val assertion = Assertion.failWith("always fail!") or Assertion.failWith("boom!")
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

  test("Assertion.and both valid") {
    val a = GenericEqualityAssertion(1, 1)
    val b = GenericEqualityAssertion(2, 2)
    assert((a and b).validated.isValid)
  }

  test("Assertion.and first invalid") {
    val a = GenericEqualityAssertion(1, 2)
    val b = GenericEqualityAssertion(2, 2)
    assert((a and b).validated.isInvalid)
  }

  test("Assertion.or one valid") {
    val a = GenericEqualityAssertion(1, 2)
    val b = GenericEqualityAssertion(2, 2)
    assert((a or b).validated.isValid)
  }

  test("Assertion.or both invalid") {
    val a = GenericEqualityAssertion(1, 2)
    val b = GenericEqualityAssertion(3, 4)
    assert((a or b).validated.isInvalid)
  }

  test("CollectionsContainSameElements with different order") {
    import io.circe.Json
    val a = Vector(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3))
    val b = Vector(Json.fromInt(3), Json.fromInt(1), Json.fromInt(2))
    assert(CollectionsContainSameElements(a, b).validated.isValid)
  }

  test("CollectionsContainSameElements with different duplicates fails") {
    import io.circe.Json
    val a = Vector(Json.fromInt(1), Json.fromInt(1), Json.fromInt(2))
    val b = Vector(Json.fromInt(1), Json.fromInt(2), Json.fromInt(2))
    assert(CollectionsContainSameElements(a, b).validated.isInvalid)
  }

}
