---
layout: docs
title:  "Property based testing support"
---

# Property based testing support

There is a support for property based testing through the `cornichon-check` module.

The initial inspiration came after reading the following article [Property based integration testing using Haskell!](https://functional.works-hub.com/learn/property-based-integration-testing-using-haskell-6c25c) which describes a way to tackle the problem of property based testing for HTTP APIs.

It is still a great introduction to the problem we are trying to solve although the implementations are significantly different.


## Concept

Performing property based testing of a pure function is quite easy, for `all` possible values, check that a given invariant is valid.

In the case of an HTTP API, it is more difficult to perform such operations, you are more often than not testing that a set of invariants are valid throughout a workflow.

The key idea is to describe the possible interactions with the API as [Markov chains](https://en.wikipedia.org/wiki/Markov_chain) which can be automatically explored.

The entry point of the `cornichon-check` DSL is reached by mixing the trait `CheckDsl` which exposes the following function:

`def check_model[A, B, C, D, E, F](maxNumberOfRuns: Int, maxNumberOfTransitions: Int, seed: Option[Long] = None)(modelRunner: ModelRunner[A, B, C, D, E, F])`

Let's unpack this signature:

- `maxNumberOfRuns` refers to maximum number of attempt to traverse the Markov chain and find a case that breaks an invariant
- `maxNumberOfTransition` is useful when the `model` contains cycles in order to ensure termination
- `seed` can be provided in order to trigger a deterministic run
- `modelRunner` is the actual definition of the `model`
- `A B C D E F` refers to the types of the `generators` used in `model` definition (maximum 6 for the moment)

Such Markov chain wires together a set of `actions` that relate to each others through `transitions` which are chosen according to a given `probability`.

Each `action` has a set of `pre-conditions` and a set of `post-conditions` that are checked automatically.

Concretely it means that:
- `pre-conditions` and `post-conditions` are `AssertSteps`
- the `action` itself is a function from a bunch of `Generators` to an `EffectStep`

Having `generators` as input enables the `action` to introduce some randomness in its effect.

A `run` terminates successfully if the max number of transition reached, this means we were not able to break any invariants.

A `run` fails if one the following conditions is met:
- a `post-condition` was broken
- an error is thrown from an `action`
- no `actions` with valid `pre-conditions` can be found, this is generally a sign of a malformed `model`
- a `generator` throws an error

A `model` exploration terminates successfully if the max number of run is reached or with an error if a run fails.

## Examples

Now that we have a common understanding of the concepts and their semantics, it is time to dive into some examples!

### Reversing strings (one generator and one transition)

Below is an example presenting the current `cornichon-check` API by checking the contract of HTTP API reversing twice a string.

We want to enforce the invariant that for any string, if we reverse it twice, it should yield the same value.

The implementation under test is a server accepting a `POST` request to `/double-reverse` with a query param named `word` will return the given `word` reversed twice.

This is silly because the Markov chain has only a single transition but it is still a good first example to show to create a `modelRunner`.

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

  //The model definition usually lives in another trait

  def stringGen(rc: RandomContext): ValueGenerator[String] = ValueGenerator(
    name = "an alphanumeric String (20)",
    genFct = () ⇒ rc.seededRandom.alphanumeric.take(20).mkString(""))

  private val generateStringAction = Action1[String](
    description = "generate and save string",
    preConditions = session_value("random-input").isAbsent :: Nil,
    effect = stringGenerator ⇒ {
      val randomString = stringGenerator()
      EffectStep.fromSyncE(s"save random string '$randomString'", _.addValue("random-input", randomString))
    },
    postConditions = session_value("random-input").isPresent :: Nil)

  private val reverseStringAction = Action1[String](
    description = "reverse a string twice yields the same value",
    preConditions = session_value("random-input").isPresent :: Nil,
    effect = _ ⇒ Attach {
      Given I post("/double-reverse").withParams("word" -> "<random-input>")
      Then assert status.is(200)
      And I save_body("reversed-twice-random-input")
    },
    postConditions = session_values("random-input", "reversed-twice-random-input").areEquals :: Nil)

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
Starting scenario 'reverse a string twice yields the same results'
- reverse a string twice yields the same results (2087 millis)

   Scenario : reverse a string twice yields the same results
      main steps
      Checking model 'reversing a string twice yields same value' with maxNumberOfRuns=5 and maxNumberOfTransitions=1 and seed=1541177654821
         Run #1
            generate and save string with values ['an alphanumeric String (20)' -> 'zCFAPwANfohFhQx4h6Pl']
            save random string 'zCFAPwANfohFhQx4h6Pl' (6 millis)
            reverse a string twice yields the same value
            Given I POST /double-reverse with query parameters 'word' -> 'zCFAPwANfohFhQx4h6Pl' (1328 millis)
            Then assert status is '200' (2 millis)
            And I save path '$' from body to key 'reversed-twice-random-input' (23 millis)
         Run #1 - End reached on action 'reverse a string twice yields the same value' after 1 transitions
         Run #2
            generate and save string with values ['an alphanumeric String (20)' -> 'jfQadaz86jXxP7AoBNST']
            save random string 'jfQadaz86jXxP7AoBNST' (0 millis)
            reverse a string twice yields the same value
            Given I POST /double-reverse with query parameters 'word' -> 'jfQadaz86jXxP7AoBNST' (6 millis)
            Then assert status is '200' (0 millis)
            And I save path '$' from body to key 'reversed-twice-random-input' (0 millis)
         Run #2 - End reached on action 'reverse a string twice yields the same value' after 1 transitions
         Run #3
            generate and save string with values ['an alphanumeric String (20)' -> 'O6SVBD9CQxUXN2Ag1mL3']
            save random string 'O6SVBD9CQxUXN2Ag1mL3' (0 millis)
            reverse a string twice yields the same value
            Given I POST /double-reverse with query parameters 'word' -> 'O6SVBD9CQxUXN2Ag1mL3' (5 millis)
            Then assert status is '200' (0 millis)
            And I save path '$' from body to key 'reversed-twice-random-input' (0 millis)
         Run #3 - End reached on action 'reverse a string twice yields the same value' after 1 transitions
         Run #4
            generate and save string with values ['an alphanumeric String (20)' -> '0nbLBgwP4eE9QqOeCbOn']
            save random string '0nbLBgwP4eE9QqOeCbOn' (0 millis)
            reverse a string twice yields the same value
            Given I POST /double-reverse with query parameters 'word' -> '0nbLBgwP4eE9QqOeCbOn' (4 millis)
            Then assert status is '200' (0 millis)
            And I save path '$' from body to key 'reversed-twice-random-input' (0 millis)
         Run #4 - End reached on action 'reverse a string twice yields the same value' after 1 transitions
         Run #5
            generate and save string with values ['an alphanumeric String (20)' -> '1RgTnx5ohrjhnZHKDHZO']
            save random string '1RgTnx5ohrjhnZHKDHZO' (0 millis)
            reverse a string twice yields the same value
            Given I POST /double-reverse with query parameters 'word' -> '1RgTnx5ohrjhnZHKDHZO' (4 millis)
            Then assert status is '200' (0 millis)
            And I save path '$' from body to key 'reversed-twice-random-input' (0 millis)
         Run #5 - End reached on action 'reverse a string twice yields the same value' after 1 transitions
      Check block succeeded (2002 millis)
```

The logs show that:
  - we have performed 5 runs of 1 transition each through the `model`
  - each run called `generateStringAction` followed by `reverseStringAction` which is the only transition defined
  - each run stopped because no other transitions are left to explore from `reverseStringAction`
  - the string generator has been called for each run
  - no post-condition has been broken

The source for the test and the server are available [here](https://github.com/agourlay/cornichon/tree/master/cornichon-check/src/test/scala/com/github/agourlay/cornichon/check/examples/stringReverse).

### Turnstile (no generator and cyclic transitions)

Having `cornichon-check` freely explore the `transitions` of a `model` can create some interesting configurations.

In this example we are going to test an HTTP API implementing a basic turnstile.

This is a rotating gate that let people pass one at the time after payment. In our simplified model it is not possible to pay for several people to pass in advance.

The server exposed two endpoints:
- a `POST` request on `/push-coin` to unlock the gate
- a `POST` request on `/walk-through` to turn the gate

```tut:silent
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.check._

class TurnstileCheck extends CornichonFeature with CheckDsl {

  def feature = Feature("Basic examples of checks") {

    Scenario("Turnstile acts according to model") {

      Given I check_model(maxNumberOfRuns = 1, maxNumberOfTransitions = 10)(turnstileModel)

    }
  }

  //Model definition usually in another trait

  private val pushCoinAction = Action0(
    description = "push a coin",
    effect = () ⇒ Given I post("/push-coin"),
    postConditions = status.is(200) :: body.is("payment accepted") :: Nil)

  private val pushCoinBlockedAction = Action0(
    description = "push a coin is a blocked",
    effect = () ⇒ Given I post("/push-coin"),
    postConditions = status.is(400) :: body.is("payment refused") :: Nil)

  private val walkThroughOkAction = Action0(
    description = "walk through ok",
    effect = () ⇒ Given I post("/walk-through"),
    postConditions = status.is(200) :: body.is("door turns") :: Nil)

  private val walkThroughBlockedAction = Action0(
    description = "walk through blocked",
    effect = () ⇒ Given I post("/walk-through"),
    postConditions = status.is(400) :: body.is("door blocked") :: Nil)

  val turnstileModel = ModelRunner.makeNoGen(
    Model(
      description = "Turnstile acts according to model",
      startingAction = pushCoinAction,
      transitions = Map(
        pushCoinAction -> ((0.9, walkThroughOkAction) :: (0.1, pushCoinBlockedAction) :: Nil),
        pushCoinBlockedAction -> ((0.9, walkThroughOkAction) :: (0.1, pushCoinBlockedAction) :: Nil),
        walkThroughOkAction -> ((0.7, pushCoinAction) :: (0.3, walkThroughBlockedAction) :: Nil),
        walkThroughBlockedAction -> ((0.9, pushCoinAction) :: (0.1, walkThroughBlockedAction) :: Nil)
      )
    )
  )
}

```

Again let's have a look at the logs to see how things go.

```
Basic examples of checks:
Starting scenario 'Turnstile acts according to model'
- Turnstile acts according to model (59 millis)

   Scenario : Turnstile acts according to model
      main steps
      Checking model 'Turnstile acts according to model' with maxNumberOfRuns=1 and maxNumberOfTransitions=10 and seed=1541225049797
         Run #1
            push a coin
            Given I POST /push-coin (9 millis)
            walk through ok
            Given I POST /walk-through (4 millis)
            push a coin
            Given I POST /push-coin (4 millis)
            walk through ok
            Given I POST /walk-through (3 millis)
            walk through blocked
            Given I POST /walk-through (3 millis)
            walk through blocked
            Given I POST /walk-through (3 millis)
            push a coin
            Given I POST /push-coin (3 millis)
            push a coin is a blocked
            Given I POST /push-coin (3 millis)
            walk through ok
            Given I POST /walk-through (3 millis)
            push a coin
            Given I POST /push-coin (3 millis)
            walk through ok
            Given I POST /walk-through (4 millis)
         Run #1 - Max transitions number per run reached
      Check block succeeded (56 millis)
```

It is interesting to note that we are executing a single run on purpose, as the server is stateful, the run would share the global state of the turnstile.

This is an issue because we are starting our model with `pushCoinAction` which is always expected to succeed.

Let's try to the same model with more run to see if it breaks!

```
Basic examples of checks:
Starting scenario 'Turnstile acts according to model'
- **failed** Turnstile acts according to model (132 millis)

  Scenario 'Turnstile acts according to model' failed:

  at step:
  Checking model 'Turnstile acts according to model' with maxNumberOfRuns=2 and maxNumberOfTransitions=10 and seed=1541251694854

  with error(s):
  A post-condition was broken for `push a coin`
  caused by:
  expected status code '200' but '400' was received with body:
  "payment refused"
  and with headers:
  'Date' -> 'Sat, 03 Nov 2018 13:28:14 GMT'
  and
  expected result was:
  '"payment accepted"'
  but actual result is:
  '"payment refused"'

  JSON patch between actual result and expected result is :
  [
    {
      "op" : "replace",
      "path" : "",
      "value" : "payment refused",
      "old" : "payment accepted"
    }
  ]

  replay only this scenario with the command:
  testOnly *TurnstileCheck -- "Turnstile acts according to model"

   Scenario : Turnstile acts according to model
      main steps
      Checking model 'Turnstile acts according to model' with maxNumberOfRuns=2 and maxNumberOfTransitions=10 and seed=1541251694854
         Run #1
            push a coin
            Given I POST /push-coin (9 millis)
            walk through ok
            Given I POST /walk-through (4 millis)
            push a coin
            Given I POST /push-coin (4 millis)
            walk through ok
            Given I POST /walk-through (4 millis)
            push a coin
            Given I POST /push-coin (4 millis)
            walk through ok
            Given I POST /walk-through (3 millis)
            walk through blocked
            Given I POST /walk-through (4 millis)
            walk through blocked
            Given I POST /walk-through (3 millis)
            push a coin
            Given I POST /push-coin (3 millis)
            walk through ok
            Given I POST /walk-through (3 millis)
            push a coin
            Given I POST /push-coin (4 millis)
         Run #1 - Max transitions number per run reached
         Run #2
            push a coin
            Given I POST /push-coin (3 millis)
            A post-condition was broken for `push a coin`
            caused by:
            expected status code '200' but '400' was received with body:
            "payment refused"
            and with headers:
            'Date' -> 'Sat, 03 Nov 2018 13:28:14 GMT'
            and
            expected result was:
            '"payment accepted"'
            but actual result is:
            '"payment refused"'

            JSON patch between actual result and expected result is :
            [
              {
                "op" : "replace",
                "path" : "",
                "value" : "payment refused",
                "old" : "payment accepted"
              }
            ]
         Run #2 - Failed
      Check model block failed  (131 millis)
```

Using 2 runs, we found already the problem because the first run finished by introducing a coin.

It is possible to replay exactly this run in a deterministic fashion by using the `seed` printed in the logs and feed it to the DSL.

`Given I check_model(maxNumberOfRuns = 2, maxNumberOfTransitions = 10, seed = Some(1541251694854L))(turnstileModel)`

This example shows that designing test scenarios with `cornichon-check` is sometimes challenging in the case of shared mutable state.

The source for the test and the server are available [here](https://github.com/agourlay/cornichon/tree/master/cornichon-check/src/test/scala/com/github/agourlay/cornichon/check/examples/turnstile).

### Web shop Admin (several generators and cyclic transitions)

// TODO

## Caveats

- all actions must have the same types within a `model` definition
- the API has a few rough edges, especially regarding type inference for the `modelRunner` definition
- placeholders generating random data such as `<random-string` and `random-uuid` are not yet using the correct `seed`
- the max number of `generators` is hard-coded to 6