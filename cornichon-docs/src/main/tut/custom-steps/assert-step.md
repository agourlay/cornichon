---
layout: docs
title:  "Assert steps"
---

# AssertStep

An `AssertStep` can be understood as the following function `Sesssion => Assertion`. Its goal is to describe an expectation.

The test engine is responsible to test the validity of the provided `Assertion` which can be one of the following:

* Equality assertions : test the equality of two objects using the cats `Equals` typeclass.
  * GenericEqualityAssertion to leave all the details to Cornichon

    ```scala
    When I AssertStep("always true!", s => GenericEqualityAssertion(true, true))
    ```

  * CustomMessageEqualityAssertion to provide a custom error message

    ```scala
    CustomMessageAssertion[A](expected: A, result: A, customMessage: () ⇒ String)
    ```

* Ordering assertions : compare two objects using the cats ```Order``` typeclass.
  * GreaterThanAssertion
  * LessThanAssertion
  * BetweenAssertion

* Collection assertions : test the state of a collection of elements
  * CollectionEmptyAssertion
  * CollectionNotEmptyAssertion
  * CollectionSizeAssertion
  * CollectionContainsAssertion

* String assertion : assert the content of a given String value
  * StringContainsAssertion
  * RegexAssertion


Below is a longer example showing how to integration an assertion into scenario.

```scala
When I EffectStep.fromSync(
  title = "estimate PI",
  action = s => s.add("result", piComputation())
)

Then assert AssertStep(
  title = "check estimate",
  action = s => Assertion.either{
    s.get("result").map(r => BetweenAssertion(3.1, r.toDouble, 3.2))
  }
)
```

`Assertions` can also be composed using `and` and `or`, for instance `BetweenAssertion` is the result of `LessThanAssertion` and `GreaterThanAssertion`.

This is rather low level therefore you not should write your steps like that directly inside the DSL but hide them behind functions with appropriate names.

Fortunately a bunch of built-in steps and primitive building blocs are already available for you.

Note for advance users: it is also possible to write custom wrapper steps by implementing `WrapperStep`.