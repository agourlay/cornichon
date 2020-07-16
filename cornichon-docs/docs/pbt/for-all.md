---
layout: docs
title:  "ForAll"
---

# ForAll

The first flavour follows the classical approach found in many testing libraries. That is, for any values from a set of generators, we will validate that a given invariant holds.

Here is the `API` available when using a single `generator`

`def for_all[A](description: String, ga: RandomContext ⇒ Generator[A])(f: A ⇒ Step): Step`

Let's look at an example to see how to use it!

We want to enforce the following invariant `for any string, if we reverse it twice, it should yield the same value`.

The implementation under test is a server accepting `POST` requests to `/double-reverse` with a query param named `word` will return the given `word` reversed twice.

```scala mdoc:silent
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.CornichonFeature

class StringReverseCheck extends CornichonFeature {

  def feature = Feature("Basic examples of checks") {

    Scenario("reverse a string twice yields the same results") {

      Given check for_all("reversing twice a string yields the same result", maxNumberOfRuns = 5, stringGen) { randomString =>
        Attach {
          Given I post("/double-reverse").withParams("word" -> randomString)
          Then assert status.is(200)
          Then assert body.is(randomString)
        }
      }
    }
  }

  def stringGen(rc: RandomContext): ValueGenerator[String] = ValueGenerator(
    name = "alphanumeric String (20)",
    gen = () => rc.alphanumeric(20))
  }

```

To understand what is going on, we can have a look at the logs produced by this scenario.

```
Starting scenario 'reverse a string twice yields the same results'
- reverse a string twice yields the same results (1848 millis)

   Scenario : reverse a string twice yields the same results
      main steps
      ForAll 'alphanumeric String (20)' check 'reversing twice a string yields the same result' with maxNumberOfRuns=5 and seed=1542985803071
         Run #0
            Given I POST /double-reverse with query parameters 'word' -> 'vtKxhkCJaVlAOzhdSCwD' (1257 millis)
            Then assert status is '200' (7 millis)
            Then assert response body is vtKxhkCJaVlAOzhdSCwD (32 millis)
         Run #0
         Run #1
            Given I POST /double-reverse with query parameters 'word' -> '1bmmb2urTfJy59J2gGtI' (5 millis)
            Then assert status is '200' (0 millis)
            Then assert response body is 1bmmb2urTfJy59J2gGtI (0 millis)
         Run #1
         Run #2
            Given I POST /double-reverse with query parameters 'word' -> 'Fg3Dzp61as7Pkvvj49ub' (5 millis)
            Then assert status is '200' (0 millis)
            Then assert response body is Fg3Dzp61as7Pkvvj49ub (0 millis)
         Run #2
         Run #3
            Given I POST /double-reverse with query parameters 'word' -> 'bDLbxzMjMgVUP1iRLu4c' (5 millis)
            Then assert status is '200' (0 millis)
            Then assert response body is bDLbxzMjMgVUP1iRLu4c (0 millis)
         Run #3
         Run #4
            Given I POST /double-reverse with query parameters 'word' -> 'byV6Azexsl1AcdatquSJ' (5 millis)
            Then assert status is '200' (0 millis)
            Then assert response body is byV6Azexsl1AcdatquSJ (0 millis)
         Run #4
         Run #5
            Given I POST /double-reverse with query parameters 'word' -> 'pKGqRrbjUV7oMaPJzTJS' (4 millis)
            Then assert status is '200' (0 millis)
            Then assert response body is pKGqRrbjUV7oMaPJzTJS (0 millis)
         Run #5
      ForAll 'alphanumeric String (20)' check 'reversing twice a string yields the same result' block succeeded (1846 millis)
```

The logs show that:

- the string generator has been called for each run
- no invariants have been broken

The source for the test and the server are available [here](https://github.com/agourlay/cornichon/tree/master/cornichon-test-framework/src/test/scala/com/github/agourlay/cornichon/framework/examples/propertyCheck/stringReverse).

More often than not, using `forAll` is enough to cover the most common use cases. But sometimes we not only want to have random values generated but also random interactions with the system under tests.
