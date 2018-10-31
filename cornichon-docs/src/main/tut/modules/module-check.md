---
layout: docs
title:  "Property based testing support"
---

# Property based testing support

There is a support for property based testing through the `cornichon-check` module.

The initial inspiration came after reading the following article [Property based integration testing using Haskell!](https://functional.works-hub.com/learn/property-based-integration-testing-using-haskell-6c25c) which describes a way to tackle the problem of property based testing for HTTP APIs.

Although the implementations are clearly different due to the `cornichon` and Scala concepts; this article remains a great introduction to the problem we are trying to solve with `cornichon-check`.


## Concept

Performing property based testing of a pure function is quite easy, for `all` possible values, check that a given invariant is valid.

In the case of an HTTP API, it is more difficult to perform such operations, you are more often than not testing that a set of invariants are valid throughout a workflow.

The key idea is to describe the possible interactions with the API as a state machine which can be automatically explored.

Such state machine wires together a set of `actions` that relate to each others through `transitions` which are chosen according to a given `probability`.

Each `action` has a set of `pre-conditions` and a set of `post-conditions` that are checked automatically.

A `run` terminates if one the following condition is met:
- one post-condition was broken
- max number of transition reached
- error thrown from an `action`
- no `actions` with valid `pre-conditions` can be found

A `model` exploration terminates if one the following condition is met:
- max number of run reached
- a run terminates with an error

Below is an example presenting the current `cornichon-check` API by checking that reversing a string twice yields the same string.

This is obviously a silly example as one would simply use `Scalacheck` for it but it has the merit of being well known.

```tut:silent
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.check._
import com.github.agourlay.cornichon.steps.regular.EffectStep

class BasicExampleChecks extends CornichonFeature with CheckDsl {

  def feature = Feature("Basic examples of checks") {

    Scenario("reverse a string twice yields the same results") {

      Given I check_model(maxNumberOfRuns = 5, maxNumberOfTransitions = 1)(doubleReverseModel)

    }
  }

  //Model definition usually in another trait

  def stringGen(rc: RandomContext): ValueGenerator[String] = ValueGenerator(
    name = "an alphanumeric String (20)",
    genFct = () ⇒ rc.seededRandom.alphanumeric.take(20).mkString(""))

  val randomInputKey = "random-input"
  val doubleReversedKey = "reversed-twice-random-input"

  private val generateStringAction = Action1[String](
    description = "generate and save string",
    preConditions = session_value(randomInputKey).isAbsent :: Nil,
    effect = generator ⇒ {
      val randomString = generator()
      EffectStep.fromSyncE(s"save random string '$randomString'", _.addValue(randomInputKey, randomString))
    },
    postConditions = session_value(randomInputKey).isPresent :: Nil)

  private val reverseStringAction = Action1[String](
    description = "retrieve and reverse a string twice yields the same value",
    preConditions = session_value(randomInputKey).isPresent :: Nil,
    effect = _ ⇒ EffectStep.fromSyncE("save reversed twice string", s ⇒ {
      for {
        value ← s.get(randomInputKey)
        reversedTwice = value.reverse.reverse
        s1 ← s.addValue(doubleReversedKey, reversedTwice)
      } yield s1
    }),
    postConditions = session_values(randomInputKey, doubleReversedKey).areEquals :: Nil)

  val doubleReverseModel = ModelRunner.make[String](stringGen)(
    Model(
      description = "reversing a string twice yields same value",
      startingAction = generateStringAction,
      transitions = Map(
        generateStringAction -> ((1.0, reverseStringAction) :: Nil)
      )
    )
  )
}

```

To understand what is going on, we can have a look at the logs produced by this scenario.

```
Basic examples of checks:
Starting scenario 'reverse a string twice yields the same results'
- reverse a string twice yields the same results (36 millis)

   Scenario : reverse a string twice yields the same results
      main steps
      Checking model 'reversing a string twice yields same value' with maxNumberOfRuns=5 and maxNumberOfTransitions=1 and seed=1540964994856
         Run #1
            generate and save string with values ['an alphanumeric String (20)' -> 'QhKJha33C3dWhkhMJI3m']
            save random string 'QhKJha33C3dWhkhMJI3m' (1 millis)
            retrieve and reverse a string twice yields the same value
            save reversed twice string (3 millis)
         Run #1 - End reached on action 'retrieve and reverse a string twice yields the same value' after 1 transitions
         Run #2
            generate and save string with values ['an alphanumeric String (20)' -> 'qTsf1o8WTDXdl6tqZEgb']
            save random string 'qTsf1o8WTDXdl6tqZEgb' (0 millis)
            retrieve and reverse a string twice yields the same value
            save reversed twice string (0 millis)
         Run #2 - End reached on action 'retrieve and reverse a string twice yields the same value' after 1 transitions
         Run #3
            generate and save string with values ['an alphanumeric String (20)' -> 'dDbT8wTgXCsfxNYcDE3l']
            save random string 'dDbT8wTgXCsfxNYcDE3l' (0 millis)
            retrieve and reverse a string twice yields the same value
            save reversed twice string (0 millis)
         Run #3 - End reached on action 'retrieve and reverse a string twice yields the same value' after 1 transitions
         Run #4
            generate and save string with values ['an alphanumeric String (20)' -> 'j4nYw07FqHdHAZdBXGR6']
            save random string 'j4nYw07FqHdHAZdBXGR6' (0 millis)
            retrieve and reverse a string twice yields the same value
            save reversed twice string (0 millis)
         Run #4 - End reached on action 'retrieve and reverse a string twice yields the same value' after 1 transitions
         Run #5
            generate and save string with values ['an alphanumeric String (20)' -> 'dLoQ4OgEYyJLLDxluUaW']
            save random string 'dLoQ4OgEYyJLLDxluUaW' (0 millis)
            retrieve and reverse a string twice yields the same value
            save reversed twice string (0 millis)
         Run #5 - End reached on action 'retrieve and reverse a string twice yields the same value' after 1 transitions
      Check block succeeded (34 millis)
```

We can see that:
  - we have performed 5 runs of 1 transition each through the state machine
  - each run called `generateStringAction` followed by `reverseAction`
  - each run stopped because no other transitions are left to explore from `reverseAction`
  - the string generator has been called for each run
  - no post-condition has been broken