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

Such state machine wires together a set of actions that relate to each others through transitions.

Each action has a set of pre-conditions and a set of post-conditions that are checked automatically.

Below is an example presenting the current `cornichon-check` API to reverse a string (this is obviously a silly example as one would simply use `Scalacheck` for it).

```tut:silent
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.check._
import com.github.agourlay.cornichon.steps.regular.EffectStep

class BasicExampleChecks extends CornichonFeature with CheckDsl {

  def stringGen(rc: RandomContext): ValueGenerator[String] = ValueGenerator(
    name = "String",
    genFct = () ⇒ rc.seededRandom.alphanumeric.take(20).mkString(""))

  def feature = Feature("Basic examples of checks") {

    Scenario("reverse string") {
      Given I check_model(maxNumberOfRuns = 5, maxNumberOfTransitions = 1)(
        modelRunner = ModelRunner.make[String](stringGen)(
          model = {
            val generateStringAction = Action1[String](
              description = "generate a string",
              preConditions = session_value("random-input").isAbsent :: Nil,
              effect = g ⇒ {
                val randomString = g()
                EffectStep.fromSyncE("save random string", _.addValue("random-input", randomString))
              },
              postConditions = session_value("random-input").isPresent :: Nil)

            val reverseAction = Action1[String](
              description = "reverse a string",
              preConditions = session_value("random-input").isPresent :: session_value("reversed-random-input").isAbsent :: Nil,
              effect = _ ⇒ EffectStep.fromSyncE("save reversed random string", s ⇒ {
                for {
                  value ← s.get("random-input")
                  reversed = value.reverse
                  s1 ← s.addValue("reversed-random-input", reversed)
                } yield s1
              }),
              postConditions = session_value("reversed-random-input").isPresent :: Nil)

            Model(
              description = "reversing a string",
              startingAction = generateStringAction,
              transitions = Map(
                generateStringAction -> ((1.0 -> reverseAction) :: Nil)
              )
            )
          }
        )
      )
    }
  }
}

```

To understand what is going on, we can have a look at the logs produced by this scenario.

```
Basic examples of checks:
Starting scenario 'reverse string'
- reverse string (44 millis)

   Scenario : reverse string
      main steps
      Checking model 'reversing a string' with maxNumberOfRuns=5 and maxNumberOfTransitions=1 and seed=1540908528908
         Run #1
            generate a string with values ['String' -> 'QcPcVIYl06vqtqHbcMCh']
            save random string (1 millis)
            reverse a string
            save reversed random string (4 millis)
         Run #1 - End reached on action 'reverse a string' after 1 transitions
         Run #2
            generate a string with values ['String' -> 'V3gRTwpsyhIQz5YNQjvC']
            save random string (0 millis)
            reverse a string
            save reversed random string (0 millis)
         Run #2 - End reached on action 'reverse a string' after 1 transitions
         Run #3
            generate a string with values ['String' -> 'fjUXGjqi9rB5Erjqn3jB']
            save random string (0 millis)
            reverse a string
            save reversed random string (0 millis)
         Run #3 - End reached on action 'reverse a string' after 1 transitions
         Run #4
            generate a string with values ['String' -> '8kWVTB7kbia67cc6m6DP']
            save random string (0 millis)
            reverse a string
            save reversed random string (0 millis)
         Run #4 - End reached on action 'reverse a string' after 1 transitions
         Run #5
            generate a string with values ['String' -> 'xOFnSeuBRWsePMKlFWlG']
            save random string (0 millis)
            reverse a string
            save reversed random string (0 millis)
         Run #5 - End reached on action 'reverse a string' after 1 transitions
      Check block succeeded (41 millis)

```

We can see that:
  - we have performed 5 runs of 1 transition each through the state machine
  - each run called `generateStringAction` followed by `reverseAction`
  - each run stopped because no other transitions are left to explore from `reverseAction`
  - the string generator has been called for each run
  - no post-condition has been broken