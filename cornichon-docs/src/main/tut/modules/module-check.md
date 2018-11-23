---
layout: docs
title:  "Property based testing support"
---

# Property based testing support

There is a support for property based testing through the `cornichon-check` module.

It offers two flavours of testing which can be used in different situations, both are available when mixing the `CheckCheck` trait.

At the center of property based testing lies the capacity to generate arbitrary values that will be used to verify if a given invariant holds.

## Generators

A `generator` is simply a function that accepts a `RandomContext` which is propagated throughout the execution, for instance below is an example generating Strings and Ints.

There are two concrete instances of `generators`:
- `ValueGenerator`
- `ValueFromSessionGenerator` which provide additionally the `Session`

```scala
def stringGen(rc: RandomContext): ValueGenerator[String] = ValueGenerator(
  name = "an alphanumeric String (20)",
  genFct = () ⇒ rc.seededRandom.alphanumeric.take(20).mkString(""))

def integerGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
  name = "integer",
  genFct = () ⇒ rc.seededRandom.nextInt(10000))
```

This approach also supports embedding `Scalacheck's Gen` into a `Generator` by propagating the initial seed.

```scala
import org.scalacheck.Gen
import org.scalacheck.rng.Seed

sealed trait Coin
case object Head extends Coin
case object Tail extends Coin

object ScalacheckExample {

  def coinGen(rc: RandomContext): ValueGenerator[Coin] = ValueGenerator(
    name = "a Coin",
    genFct = () ⇒ {
      val nextSeed = rc.seededRandom.nextLong()
      val params = Gen.Parameters.default.withInitialSeed(nextSeed)
      val coin = Gen.oneOf[Coin](Head, Tail)
      coin(params, Seed(nextSeed)).get
    }
  )

}
```

Those are `ValueGenerators`

## First flavour - ∀

The first flavour follows the classical approach found in many testing libraries. That is for any values from a set of generators, we will validate that a given invariant holds.

Here is the `API` available when using a single `generator`

`def for_all[A](description: String, ga: RandomContext ⇒ Generator[A])(f: A ⇒ Step): Step`

Let's use an example to see how to use it!

We want to enforce the following invariant `for any string, if we reverse it twice, it should yield the same value`.

The implementation under test is a server accepting `POST` requests to `/double-reverse` with a query param named `word` will return the given `word` reversed twice.

```tut:silent
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.check._
import com.github.agourlay.cornichon.steps.regular.EffectStep

class StringReverseCheck extends CornichonFeature with CheckDsl {

  def feature = Feature("Basic examples of checks") {

    Scenario("reverse a string twice yields the same results") {
 
      Given check for_all("reversing twice a string yields the same result", maxNumberOfRuns = 5, stringGen) { randomString ⇒
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
    genFct = () ⇒ rc.seededRandom.alphanumeric.take(20).mkString(""))
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

The source for the test and the server are available [here](https://github.com/agourlay/cornichon/tree/master/cornichon-check/src/test/scala/com/github/agourlay/cornichon/check/examples/stringReverse).

More often that not, using `forAll` is enough to cover the most common use cases. But sometimes, we want not only to have random values generated but also random interactions with the system under tests.

## Second flavour - Random model exploration

The initial inspiration came after reading the following article [Property based integration testing using Haskell!](https://functional.works-hub.com/learn/property-based-integration-testing-using-haskell-6c25c) which describes a way to tackle the problem of property based testing for HTTP APIs.

It is still a great introduction to the problem we are trying to solve although the implementations are significantly different.

### Concepts

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
- `A B C D E F` refers to the types of the `generators` used in `model` definition (maximum of 6 for the moment)

Such Markov chain wires together a set of `properties` that relate to each others through `transitions` which are chosen according to a given `probability`.

A `property` is composed of:
- a description
- an optional `pre-condition` which is a `step` checking that the `property` can be run (sometimes useful to target error cases)
- an `invariant` which is a function from a number of `generators` to a `step` performing whatever side effect and assertions necessary

The number of generators is defined in the `property` type:
- `Property0` an action which accepts a function from `() => Step`
- `Property1[A]` an action which accepts a function from `() ⇒ A => Step`
- `Property2[A, B]` an action which accepts a function from `(() ⇒ A, () => B) => Step`
- `Property3[A, B, C]` an action which accepts a function from `(() ⇒ A, () => B, () => C) => Step`
- up to `Property6[A, B, C, D, E, F]`

It is of course not required to call a generator when building a `Step`.

*However it is required to have the same `Property` type for all properties within a `model` definition.*

Having `generators` as input enables the `action` to introduce some randomness in its effect.

A `run` terminates successfully if the max number of transition reached, this means we were not able to break any invariants.

A `run` fails if one the following conditions is met:
- an error is thrown from a `property`
- no `properties` with a valid `pre-condition` can be found, this is generally a sign of a malformed `model`
- a `generator` throws an error

A `model` exploration terminates successfully if the max number of run is reached or with an error if a run fails.

Let's create our first `model`!

It will be a basic chain which will not enforce any invariants; it will have:

- an entry point
- a ping `property` printing a random String
- a pong `property` printing a random Int
- an exit point

We will define the transitions such that:

- there is 50% chance to start with ping or pong following the entry point
- there is 90% to go from a ping/pong to a pong/ping
- there is no loop from any `property`
- there is a 10% chance to exit the game after a ping or a pong

Also the DSL is asking for a `modelRunner` which is a little helper connecting a `model` to its `generators`.

The type inference is sometimes not detecting properly the action type, so it is recommended to define the `modelRunner` and the `model` as a single expression to help the typechecker.

```scala

def stringGen(rc: RandomContext): ValueGenerator[String] = ValueGenerator(
  name = "an alphanumeric String",
  genFct = () ⇒ rc.seededRandom.alphanumeric.take(20).mkString(""))

def integerGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
  name = "integer",
  genFct = () ⇒ rc.seededRandom.nextInt(10000))

val myModelRunner = ModelRunner.make[String, Int](stringGen, integerGen) {

  val entryPoint = Property2[String, Int](
    description = "Entry point",
    invariant = (_, _) ⇒ print_step("Start game")
  )

  val pingString = Property2[String, Int](
    description = "Ping String",
    invariant = (stringGen, _) ⇒ print_step(s"Ping ${stringGen()}")
  )

  val pongInt = Property2[String, Int](
    description = "Pong Int",
    invariant = (_, intGen) ⇒ print_step(s"Pong ${intGen()}")
  )

  val exitPoint = Property2[String, Int](
    description = "Exit point",
    invariant = (_, _) ⇒ print_step("End of game")
  )

  Model(
    description = "ping pong model",
    entryPoint = entryPoint,
    transitions = Map(
      entryPoint -> ((0.5, pingString) :: (0.5, pongInt) :: Nil),
      pingString -> ((0.9, pongInt) :: (0.1, exitPoint) :: Nil),
      pongInt -> ((0.9, pingString) :: (0.1, exitPoint) :: Nil)
    )
  )
}
```


Which gives us the following scenario

```

Scenario("ping pong check) {

  Given I check_model(maxNumberOfRuns = 2, maxNumberOfTransitions = 10)(myModelRunner)

}
```

Running this scenario outputs:

```
Starting scenario 'ping pong check'
- ping pong check (10 millis)

   Scenario : ping pong check
      main steps
      Checking model 'ping pong model' with maxNumberOfRuns=2 and maxNumberOfTransitions=10 and seed=1542986106586
         Run #1
            Entry point
            Start game
            Ping String
            Ping 7HjRBzlyjULWQlV1SQeN
            Pong Int
            Pong 3549
            Ping String
            Ping SbL4blEMtwweAqm9bfP0
            Pong Int
            Pong 1464
            Ping String
            Ping BoZwLouAaXVayxXajSXV
            Pong Int
            Pong 161
            Ping String
            Ping BOCm8OyLL2zgpPCoYnTJ
            Pong Int
            Pong 687
            Ping String
            Ping d9ZRN1HuBhVwFKXBzlUh
            Pong Int
            Pong 1892
         Run #1 - Max transitions number per run reached
         Run #2
            Entry point
            Start game
            Ping String
            Ping oHARiIS8570hOHbkpu6b
            Pong Int
            Pong 743
            Ping String
            Ping Ijq1xltbS2fGIVJ0h3ty
            Pong Int
            Pong 7575
            Ping String
            Ping 15DDNEIATOrbKnDQi9QI
            Pong Int
            Pong 4674
            Ping String
            Ping YNvchmkwK7owS95YeyXr
            Pong Int
            Pong 3758
            Ping String
            Ping DavIeJhxwOpgmqXZrzOU
            Exit point
            End of game
         Run #2 - End reached on property 'Exit point' after 10 transitions
      Check block succeeded (10 millis)

```

// TODO talk about fixing seed to fix generators and transitions

### Examples

Now that we have an understanding of the concepts and their semantics, it is time to dive into some concrete examples!

#### Turnstile (no generator and cyclic transitions)

Having `cornichon-check` freely explore the `transitions` of a `model` can create some interesting configurations.

In this example we are going to test an HTTP API implementing a basic turnstile.

This is a rotating gate that let people pass one at the time after payment. In our simplified model it is not possible to pay for several people to pass in advance.

The server exposed two endpoints:
- a `POST` request on `/push-coin` to unlock the gate
- a `POST` request on `/walk-through` to turn the gate

```tut:silent
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.check.checkModel._
import com.github.agourlay.cornichon.check._

class TurnstileCheck extends CornichonFeature with CheckDsl {

  def feature = Feature("Basic examples of checks") {

    Scenario("Turnstile acts according to model") {

      Given I check_model(maxNumberOfRuns = 1, maxNumberOfTransitions = 10)(turnstileModel)

    }
  }

  //Model definition usually in another trait

  private val pushCoin = Property0(
    description = "push a coin",
    invariant = () ⇒ Attach {
      Given I post("/push-coin")
      Then assert status.is(200)
      And assert body.is("payment accepted")
    })

  private val pushCoinBlocked = Property0(
    description = "push a coin is a blocked",
    invariant = () ⇒ Attach {
      Given I post("/push-coin")
      Then assert status.is(400)
      And assert body.is("payment refused")
    })

  private val walkThroughOk = Property0(
    description = "walk through ok",
    invariant = () ⇒ Attach {
      Given I post("/walk-through")
      Then assert status.is(200)
      And assert body.is("door turns")
    })

  private val walkThroughBlocked = Property0(
    description = "walk through blocked",
    invariant = () ⇒ Attach {
      Given I post("/walk-through")
      Then assert status.is(400)
      And assert body.is("door blocked")
    })

  val turnstileModel = ModelRunner.makeNoGen(
    Model(
      description = "Turnstile acts according to model",
      entryPoint = pushCoin,
      transitions = Map(
        pushCoin -> ((0.9, walkThroughOk) :: (0.1, pushCoinBlocked) :: Nil),
        pushCoinBlocked -> ((0.9, walkThroughOk) :: (0.1, pushCoinBlocked) :: Nil),
        walkThroughOk -> ((0.7, pushCoin) :: (0.3, walkThroughBlocked) :: Nil),
        walkThroughBlocked -> ((0.9, pushCoin) :: (0.1, walkThroughBlocked) :: Nil)
      )
    )
  )
}

```

Again let's have a look at the logs to see how things go.

```
Starting scenario 'Turnstile acts according to model'
- Turnstile acts according to model (55 millis)

   Scenario : Turnstile acts according to model
      main steps
      Checking model 'Turnstile acts according to model' with maxNumberOfRuns=1 and maxNumberOfTransitions=10 and seed=1542021399582
         Run #1
            push a coin
            Given I POST /push-coin (9 millis)
            Then assert status is '200' (0 millis)
            And assert response body is payment accepted (0 millis)
            push a coin is a blocked
            Given I POST /push-coin (5 millis)
            Then assert status is '400' (0 millis)
            And assert response body is payment refused (0 millis)
            walk through ok
            Given I POST /walk-through (3 millis)
            Then assert status is '200' (0 millis)
            And assert response body is door turns (0 millis)
            push a coin
            Given I POST /push-coin (3 millis)
            Then assert status is '200' (0 millis)
            And assert response body is payment accepted (0 millis)
            walk through ok
            Given I POST /walk-through (3 millis)
            Then assert status is '200' (0 millis)
            And assert response body is door turns (0 millis)
            walk through blocked
            Given I POST /walk-through (3 millis)
            Then assert status is '400' (0 millis)
            And assert response body is door blocked (0 millis)
            push a coin
            Given I POST /push-coin (3 millis)
            Then assert status is '200' (0 millis)
            And assert response body is payment accepted (0 millis)
            walk through ok
            Given I POST /walk-through (3 millis)
            Then assert status is '200' (0 millis)
            And assert response body is door turns (0 millis)
            push a coin
            Given I POST /push-coin (3 millis)
            Then assert status is '200' (0 millis)
            And assert response body is payment accepted (0 millis)
            walk through ok
            Given I POST /walk-through (3 millis)
            Then assert status is '200' (0 millis)
            And assert response body is door turns (0 millis)
            push a coin
            Given I POST /push-coin (3 millis)
            Then assert status is '200' (0 millis)
            And assert response body is payment accepted (0 millis)
         Run #1 - Max transitions number per run reached
      Check block succeeded (54 millis)
```

It is interesting to note that we are executing a single run on purpose, as the server is stateful, any subsequent runs would share the global state of the turnstile.

This is an issue because we are starting our model with `pushCoinAction` which is always expected to succeed.

Let's try to the same model with more run to see if it breaks!

```
Starting scenario 'Turnstile acts according to model'
- **failed** Turnstile acts according to model (74 millis)

  Scenario 'Turnstile acts according to model' failed:

  at step:
  Then assert status is '200'

  with error(s):
  expected status code '200' but '400' was received with body:
  "payment refused"
  and with headers:
  'Date' -> 'Mon, 12 Nov 2018 11:18:03 GMT'

  replay only this scenario with the command:
  testOnly *TurnstileCheck -- "Turnstile acts according to model"

   Scenario : Turnstile acts according to model
      main steps
      Checking model 'Turnstile acts according to model' with maxNumberOfRuns=2 and maxNumberOfTransitions=10 and seed=1542021482941
         Run #1
            push a coin
            Given I POST /push-coin (11 millis)
            Then assert status is '200' (0 millis)
            And assert response body is payment accepted (0 millis)
            walk through ok
            Given I POST /walk-through (4 millis)
            Then assert status is '200' (0 millis)
            And assert response body is door turns (0 millis)
            push a coin
            Given I POST /push-coin (3 millis)
            Then assert status is '200' (0 millis)
            And assert response body is payment accepted (0 millis)
            walk through ok
            Given I POST /walk-through (3 millis)
            Then assert status is '200' (0 millis)
            And assert response body is door turns (0 millis)
            push a coin
            Given I POST /push-coin (4 millis)
            Then assert status is '200' (0 millis)
            And assert response body is payment accepted (0 millis)
            walk through ok
            Given I POST /walk-through (3 millis)
            Then assert status is '200' (0 millis)
            And assert response body is door turns (0 millis)
            push a coin
            Given I POST /push-coin (3 millis)
            Then assert status is '200' (0 millis)
            And assert response body is payment accepted (0 millis)
            walk through ok
            Given I POST /walk-through (3 millis)
            Then assert status is '200' (0 millis)
            And assert response body is door turns (0 millis)
            push a coin
            Given I POST /push-coin (3 millis)
            Then assert status is '200' (0 millis)
            And assert response body is payment accepted (0 millis)
            walk through ok
            Given I POST /walk-through (3 millis)
            Then assert status is '200' (0 millis)
            And assert response body is door turns (0 millis)
            push a coin
            Given I POST /push-coin (3 millis)
            Then assert status is '200' (0 millis)
            And assert response body is payment accepted (0 millis)
         Run #1 - Max transitions number per run reached
         Run #2
            push a coin
            Given I POST /push-coin (3 millis)
            Then assert status is '200' *** FAILED ***
            expected status code '200' but '400' was received with body:
            "payment refused"
            and with headers:
            'Date' -> 'Mon, 12 Nov 2018 11:18:03 GMT'
         Run #2 - Failed
      Check model block failed  (74 millis)
```

Using 2 runs, we found already the problem because the first run finished by introducing a coin.

It is possible to replay exactly this run in a deterministic fashion by using the `seed` printed in the logs and feed it to the DSL.

`Given I check_model(maxNumberOfRuns = 2, maxNumberOfTransitions = 10, seed = Some(1542021482941L))(turnstileModel)`

This example shows that designing test scenarios with `cornichon-check` is sometimes challenging in the case of shared mutable state.

The source for the test and the server are available [here](https://github.com/agourlay/cornichon/tree/master/cornichon-check/src/test/scala/com/github/agourlay/cornichon/check/examples/turnstile).

#### Web shop Admin (several generators and cyclic transitions)

// TODO

### Caveats

- all `properties` must have the same types within a `model` definition
- the API has a few rough edges, especially regarding type inference for the `modelRunner` definition
- placeholders generating random data such as `<random-string` and `random-uuid` are not yet using the correct `seed`
- the max number of `generators` is hard-coded to 6