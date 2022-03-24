---
layout: docs
title:  "Random model exploration"
---

# Random model exploration

The initial inspiration came after reading the following article [Property based integration testing using Haskell!](https://functional.works-hub.com/learn/property-based-integration-testing-using-haskell-6c25c) which describes a way to tackle the problem of property based testing for HTTP APIs.

It is still a great introduction to the problem we are trying to solve, although the implementations are significantly different.

## Concepts

Performing property based testing of a pure function is quite easy, for `all` possible values, check that a given invariant is valid.

In the case of an HTTP API, it is more difficult to perform such operations, you are more often than not testing that a set of invariants are valid throughout a workflow.

The key idea is to describe the possible interactions with the API as [Markov chains](https://en.wikipedia.org/wiki/Markov_chain) which can be automatically explored.

The entry point for random model exploration is the following function in the DSL:

`def check_model[A, B, C, D, E, F](maxNumberOfRuns: Int, maxNumberOfTransitions: Int)(modelRunner: ModelRunner[A, B, C, D, E, F])`

Let's unpack this signature:

- `maxNumberOfRuns` refers to maximum number of attempt to traverse the Markov chain and find a case that breaks an invariant
- `maxNumberOfTransition` is useful when the `model` contains cycles in order to ensure termination
- `modelRunner` is the actual definition of the `model`
- `A B C D E F` refers to the types of the `generators` used in `model` definition (maximum of 6 for the moment)

Such a Markov chain wires together a set of `properties` that relate to each other through `transitions` which are chosen according to a given `probability` (between 0 and 100).

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

*However, it is required to have the same `Property` type for all properties within a `model` definition.*

Having `generators` as input enables the `action` to introduce some randomness in its effect.

A `run` terminates successfully if the max number of transition reached, this means we were not able to break any invariants.

A `run` fails if one of the following conditions is met:
- an error is thrown from a `property`
- no `properties` with a valid `pre-condition` can be found, this is generally a sign of a malformed `model`
- a `generator` throws an error

A `model` exploration terminates successfully if the max number of runs is reached or with an error if a run fails.

Let's create our first `model`!

It will be a basic chain which will not enforce any invariants; it will have:

- an entry point
- a ping `property` printing a random String
- a pong `property` printing a random Int
- an exit point

We will define the transitions such that:

- there is a 50% chance to start with ping or pong following the entry point
- there is 90% to go from a ping/pong to a pong/ping
- there is no loop from any `property`
- there is a 10% chance to exit the game after a ping or a pong

Also, the DSL is asking for a `modelRunner` which is a little helper connecting a `model` to its `generators`.

The type inference is sometimes not properly detecting the action type, so it is recommended to define the `modelRunner` and the `model` as a single expression to help the typechecker.

```scala

def stringGen(rc: RandomContext): ValueGenerator[String] = ValueGenerator(
  name = "an alphanumeric String",
  gen = () ⇒ rc.alphanumeric.take(20).mkString(""))

def integerGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
  name = "integer",
  gen = () ⇒ rc.nextInt(10000))

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
      entryPoint -> ((50, pingString) :: (50, pongInt) :: Nil),
      pingString -> ((90, pongInt) :: (10, exitPoint) :: Nil),
      pongInt -> ((90, pingString) :: (10, exitPoint) :: Nil)
    )
  )
}
```


Which gives us the following scenario

```scala

Scenario("ping pong check") {

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

The logs give us:
- a detailed description of the runs execution
- the seed used to create the `Generators`

It is possible to replay exactly the same run by passing the seed as a parameter of the feature or by a CLI argument.

## Examples

Now that we have a better understanding of the concepts and their semantics, it is time to dive into some concrete examples!

Having `cornichon` freely explore the `transitions` of a `model` can create some interesting configurations.

### Turnstile

In this example we are going to test an HTTP API implementing a basic turnstile.

This is a rotating gate that let people pass one at the time after payment. In our simplified model it is not possible to pay for several people to pass in advance.

The server exposes two endpoints:
- a `POST` request on `/push-coin` to unlock the gate
- a `POST` request on `/walk-through` to turn the gate

```scala mdoc:silent
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.steps.check.checkModel._

class TurnstileCheck extends CornichonFeature {

  def feature = Feature("Basic examples of checks") {

    Scenario("Turnstile acts according to model") {

      Given I check_model(maxNumberOfRuns = 1, maxNumberOfTransitions = 10)(turnstileModel)

    }
  }

  //Model definition usually in another trait

  private val pushCoin = Property0(
    description = "push a coin",
    invariant = () => Attach {
      Given I post("/push-coin")
      Then assert status.is(200)
      And assert body.is("payment accepted")
    })

  private val pushCoinBlocked = Property0(
    description = "push a coin is a blocked",
    invariant = () => Attach {
      Given I post("/push-coin")
      Then assert status.is(400)
      And assert body.is("payment refused")
    })

  private val walkThroughOk = Property0(
    description = "walk through ok",
    invariant = () => Attach {
      Given I post("/walk-through")
      Then assert status.is(200)
      And assert body.is("door turns")
    })

  private val walkThroughBlocked = Property0(
    description = "walk through blocked",
    invariant = () => Attach {
      Given I post("/walk-through")
      Then assert status.is(400)
      And assert body.is("door blocked")
    })

  val turnstileModel = ModelRunner.makeNoGen(
    Model(
      description = "Turnstile acts according to model",
      entryPoint = pushCoin,
      transitions = Map(
        pushCoin -> ((90, walkThroughOk) :: (10, pushCoinBlocked) :: Nil),
        pushCoinBlocked -> ((90, walkThroughOk) :: (10, pushCoinBlocked) :: Nil),
        walkThroughOk -> ((70, pushCoin) :: (30, walkThroughBlocked) :: Nil),
        walkThroughBlocked -> ((90, pushCoin) :: (10, walkThroughBlocked) :: Nil)
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

Let's try to test the same model with more runs to see if it breaks!

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

Using 2 runs, we already found the problem because the first run finished by introducing a coin.

It is possible to replay exactly this run in a deterministic fashion by using the `seed` printed in the logs and feed it to the DSL.

`Given I check_model(maxNumberOfRuns = 2, maxNumberOfTransitions = 10)(turnstileModel)`

This example shows that designing property based scenarios is sometimes challenging in the case of shared mutable states.

The source for the test and the server are available [here](https://github.com/agourlay/cornichon/tree/master/cornichon-test-framework/src/test/scala/com/github/agourlay/cornichon/framework/examples/propertyCheck/turnstile).

### Web shop Admin (advanced example)

In this example we are going to test an HTTP API implementing a basic web shop.

This web shop offers the possibility to `CRUD` products and to search them via an index that is eventually consistent.

A product is defined by the following case class.

```scala
case class Product(id: UUID, name: String, description: String, price: BigInt)

case class ProductDraft(name: String, description: String, price: BigInt)
```

The server exposes the following endpoint:
- a `POST` request on `/products` to create a product via productDraft
- a `POST` request on `/products/<id>` to update a product
- a `DELETE` request on `/products/<id>` to delete a product
- a `GET` request on `/products` to get all products
- a `GET` request on `/products/<id>` to get a single product
- a `GET` request on `products-search` to get all the products in the search index

The contract is that the consistency delay should always be under 10 seconds for all operations being mirrored in the search index.

Let's see if we can test it!

```scala
package com.github.agourlay.cornichon.check.examples.webShop


class WebShopCheck extends CornichonFeature {

  def feature = Feature("Advanced example of model checks") {

    Scenario("WebShop acts according to model") {

      Given I check_model(maxNumberOfRuns = 1, maxNumberOfTransitions = 5)(webShopModel)

    }
  }

  val maxIndexSyncTimeout = 10.seconds

  def productDraftGen(rc: RandomContext): Generator[ProductDraft] = OptionalValueGenerator(
    name = "a product draft",
    gen = () ⇒ {
      val nextSeed = rc.nextLong()
      val params = Gen.Parameters.default.withInitialSeed(nextSeed)
      val gen =
        for {
          name ← Gen.alphaStr
          description ← Gen.alphaStr
          price ← Gen.choose(1, Int.MaxValue)
        } yield ProductDraft(name, description, price)
      gen(params, Seed(nextSeed))
    }
  )

  private val noProductsInDb = Property1[ProductDraft](
    description = "no products in DB",
    invariant = _ ⇒ Attach {
      Given I get("/products")
      Then assert status.is(200)
      Then assert body.asArray.isEmpty
    }
  )

  private val createProduct = Property1[ProductDraft](
    description = "create a product",
    invariant = pd ⇒ {
      val productDraft = pd()
      val productDraftJson = productDraft.asJson
      Attach {
        Given I post("/products").withBody(productDraftJson)
        Then assert status.is(201)
        And assert body.ignoring("id").is(productDraftJson)
        Eventually(maxDuration = maxIndexSyncTimeout, interval = 10.millis) {
          When I get("/products-search")
          Then assert status.is(200)
          And assert body.asArray.ignoringEach("id").contains(productDraftJson)
        }
      }
    })

  private val deleteProduct = Property1[ProductDraft](
    description = "delete a product",
    preCondition = Attach {
      Given I get("/products")
      Then assert body.asArray.isNotEmpty
    },
    invariant = _ ⇒ Attach {
      Given I get("/products")
      Then assert status.is(200)
      Then I save_body_path("$[0].id" -> "id-to-delete")
      Given I delete("/products/<id-to-delete>")
      Then assert status.is(200)
      And I get("/products/<id-to-delete>")
      Then assert status.is(404)
      Eventually(maxDuration = maxIndexSyncTimeout, interval = 10.millis) {
        When I get("/products-search")
        Then assert status.is(200)
        And assert body.path("$[*].id").asArray.not_contains("<id-to-delete>")
      }
    }
  )

  private val updateProduct = Property1[ProductDraft](
    description = "update a product",
    preCondition = Attach {
      Given I get("/products")
      Then assert body.asArray.isNotEmpty
    },
    invariant = pd ⇒ {
      val productDraft = pd()
      val productDraftJson = productDraft.asJson
      Attach {
        Given I get("/products")
        Then assert status.is(200)
        Then I save_body_path("$[0].id" -> "id-to-update")
        Given I post("/products/<id-to-update>").withBody(productDraftJson)
        Then assert status.is(201)
        And I get("/products/<id-to-update>")
        Then assert status.is(200)
        And assert body.ignoring("id").is(productDraftJson)
        Eventually(maxDuration = maxIndexSyncTimeout, interval = 10.millis) {
          When I get("/products-search")
          Then assert status.is(200)
          And assert body.asArray.ignoringEach("id").contains(productDraftJson)
        }
      }
    }
  )

  val webShopModel = ModelRunner.make[ProductDraft](productDraftGen)(
    Model(
      description = "WebShop acts according to specification",
      entryPoint = noProductsInDb,
      transitions = Map(
        noProductsInDb -> ((100, createProduct) :: Nil),
        createProduct -> ((60, createProduct) :: (30, updateProduct) :: (10, deleteProduct) :: Nil),
        deleteProduct -> ((60, createProduct) :: (30, updateProduct) :: (10, deleteProduct) :: Nil),
        updateProduct -> ((60, createProduct) :: (30, updateProduct) :: (10, deleteProduct) :: Nil)
      )
    )
  )
}
```

Again, let's have a look at the logs to see how things go.

```
Advanced example of model checks:
Starting scenario 'WebShop acts according to model'
- WebShop acts according to model (42747 millis)

   Scenario : WebShop acts according to model
      main steps
      Checking model 'WebShop acts according to specification' with maxNumberOfRuns=1 and maxNumberOfTransitions=5 and seed=1544540492543
         Run #1
            no products in DB
            Given I GET /products (1328 millis)
            Then assert status is '200' (10 millis)
            Then assert response body array size is '0' (15 millis)
            create a product
            Given I POST /products with body (59 millis)
            {
              "name" : "rzdikphaxkjroaoYguPblwnwxdnnnrokkjhscTfmwfanhrsXsamphz",
              "description" : "flaukuIykZksgwsxznkayAiiojwgndtiffT",
              "price" : 32
            }
            Then assert status is '201' (0 millis)
            And assert response body is (41 millis)
            {
              "name" : "rzdikphaxkjroaoYguPblwnwxdnnnrokkjhscTfmwfanhrsXsamphz",
              "description" : "flaukuIykZksgwsxznkayAiiojwgndtiffT",
              "price" : 32
            } ignoring keys id
            Eventually block with maxDuration = 10 seconds and interval = 10 milliseconds
               When I GET /products-search (9 millis)
               Then assert status is '200' (0 millis)
               And assert response body array contains
               {
                 "name" : "rzdikphaxkjroaoYguPblwnwxdnnnrokkjhscTfmwfanhrsXsamphz",
                 "description" : "flaukuIykZksgwsxznkayAiiojwgndtiffT",
                 "price" : 32
               } *** FAILED ***
               expected array to contain
               '{
                 "name" : "rzdikphaxkjroaoYguPblwnwxdnnnrokkjhscTfmwfanhrsXsamphz",
                 "description" : "flaukuIykZksgwsxznkayAiiojwgndtiffT",
                 "price" : 32
               }'
               but it is not the case with array:
               [
               ]
               When I GET /products-search (5 millis)
               Then assert status is '200' (0 millis)
               And assert response body array contains (0 millis)
               {
                 "name" : "rzdikphaxkjroaoYguPblwnwxdnnnrokkjhscTfmwfanhrsXsamphz",
                 "description" : "flaukuIykZksgwsxznkayAiiojwgndtiffT",
                 "price" : 32
               }
            Eventually block succeeded after '552' retries with '1' distinct errors (8972 millis)
            update a product
            Given I GET /products (2 millis)
            Then assert status is '200' (0 millis)
            Then I save path '$[0].id' from body to key 'id-to-update' (8 millis)
            Given I POST /products/ee668765-b274-462d-ace8-73e2464286ca with body (11 millis)
            {
              "name" : "usogcaoeNkEgmaqybjpoisbtizlFkNyyrvvgCtbwoxdzxijwwdugmJZeksdBFbvcXn",
              "description" : "wflinKgfiguhNjkhwepgsdUmylbrXsjvhEnUccHzqglrpaiksaEtlbgmhfocAWjvieTwva",
              "price" : 46
            }
            Then assert status is '201' (0 millis)
            And I GET /products/ee668765-b274-462d-ace8-73e2464286ca (6 millis)
            Then assert status is '200' (0 millis)
            And assert response body is (0 millis)
            {
              "name" : "usogcaoeNkEgmaqybjpoisbtizlFkNyyrvvgCtbwoxdzxijwwdugmJZeksdBFbvcXn",
              "description" : "wflinKgfiguhNjkhwepgsdUmylbrXsjvhEnUccHzqglrpaiksaEtlbgmhfocAWjvieTwva",
              "price" : 46
            } ignoring keys id
            Eventually block with maxDuration = 10 seconds and interval = 10 milliseconds
               When I GET /products-search (2 millis)
               Then assert status is '200' (0 millis)
               And assert response body array contains
               {
                 "name" : "usogcaoeNkEgmaqybjpoisbtizlFkNyyrvvgCtbwoxdzxijwwdugmJZeksdBFbvcXn",
                 "description" : "wflinKgfiguhNjkhwepgsdUmylbrXsjvhEnUccHzqglrpaiksaEtlbgmhfocAWjvieTwva",
                 "price" : 46
               } *** FAILED ***
               expected array to contain
               '{
                 "name" : "usogcaoeNkEgmaqybjpoisbtizlFkNyyrvvgCtbwoxdzxijwwdugmJZeksdBFbvcXn",
                 "description" : "wflinKgfiguhNjkhwepgsdUmylbrXsjvhEnUccHzqglrpaiksaEtlbgmhfocAWjvieTwva",
                 "price" : 46
               }'
               but it is not the case with array:
               [
                 {
                   "id" : "ee668765-b274-462d-ace8-73e2464286ca",
                   "name" : "rzdikphaxkjroaoYguPblwnwxdnnnrokkjhscTfmwfanhrsXsamphz",
                   "description" : "flaukuIykZksgwsxznkayAiiojwgndtiffT",
                   "price" : 32
                 }
               ]
               When I GET /products-search (2 millis)
               Then assert status is '200' (0 millis)
               And assert response body array contains (0 millis)
               {
                 "name" : "usogcaoeNkEgmaqybjpoisbtizlFkNyyrvvgCtbwoxdzxijwwdugmJZeksdBFbvcXn",
                 "description" : "wflinKgfiguhNjkhwepgsdUmylbrXsjvhEnUccHzqglrpaiksaEtlbgmhfocAWjvieTwva",
                 "price" : 46
               }
            Eventually block succeeded after '616' retries with '1' distinct errors (8996 millis)
            delete a product
            Given I GET /products (1 millis)
            Then assert status is '200' (0 millis)
            Then I save path '$[0].id' from body to key 'id-to-delete' (0 millis)
            Given I DELETE /products/ee668765-b274-462d-ace8-73e2464286ca (2 millis)
            Then assert status is '200' (0 millis)
            And I GET /products/ee668765-b274-462d-ace8-73e2464286ca (1 millis)
            Then assert status is '404' (0 millis)
            Eventually block with maxDuration = 10 seconds and interval = 10 milliseconds
               When I GET /products-search (1 millis)
               Then assert status is '200' (0 millis)
               And assert response body's array '$[*].id' does not contain
               ee668765-b274-462d-ace8-73e2464286ca *** FAILED ***
               expected array to not contain
               '"ee668765-b274-462d-ace8-73e2464286ca"'
               but it is not the case with array:
               [
                 "ee668765-b274-462d-ace8-73e2464286ca"
               ]
               When I GET /products-search (2 millis)
               Then assert status is '200' (0 millis)
               And assert response body's array '$[*].id' does not contain (0 millis)
               ee668765-b274-462d-ace8-73e2464286ca
            Eventually block succeeded after '571' retries with '1' distinct errors (8001 millis)
            create a product
            Given I POST /products with body (2 millis)
            {
              "name" : "kskqbczgcepmzvoybdKmpniflncdWqbqejtLcj",
              "description" : "krrzzpmbwLyxsfiwxhdlnoycnfk",
              "price" : 77
            }
            Then assert status is '201' (0 millis)
            And assert response body is (0 millis)
            {
              "name" : "kskqbczgcepmzvoybdKmpniflncdWqbqejtLcj",
              "description" : "krrzzpmbwLyxsfiwxhdlnoycnfk",
              "price" : 77
            } ignoring keys id
            Eventually block with maxDuration = 10 seconds and interval = 10 milliseconds
               When I GET /products-search (1 millis)
               Then assert status is '200' (0 millis)
               And assert response body array contains
               {
                 "name" : "kskqbczgcepmzvoybdKmpniflncdWqbqejtLcj",
                 "description" : "krrzzpmbwLyxsfiwxhdlnoycnfk",
                 "price" : 77
               } *** FAILED ***
               expected array to contain
               '{
                 "name" : "kskqbczgcepmzvoybdKmpniflncdWqbqejtLcj",
                 "description" : "krrzzpmbwLyxsfiwxhdlnoycnfk",
                 "price" : 77
               }'
               but it is not the case with array:
               [
               ]
               When I GET /products-search (1 millis)
               Then assert status is '200' (0 millis)
               And assert response body array contains (0 millis)
               {
                 "name" : "kskqbczgcepmzvoybdKmpniflncdWqbqejtLcj",
                 "description" : "krrzzpmbwLyxsfiwxhdlnoycnfk",
                 "price" : 77
               }
            Eventually block succeeded after '671' retries with '1' distinct errors (9003 millis)
            create a product
            Given I POST /products with body (2 millis)
            {
              "name" : "mrhwfmp",
              "description" : "hBvcqpyatdyjccokfvXtbppejlSfnuGgmahAclfjCqmzCmdqydNsiYlICpZnwgqxzVkzrqnoyucZtbgoguc",
              "price" : 18
            }
            Then assert status is '201' (0 millis)
            And assert response body is (0 millis)
            {
              "name" : "mrhwfmp",
              "description" : "hBvcqpyatdyjccokfvXtbppejlSfnuGgmahAclfjCqmzCmdqydNsiYlICpZnwgqxzVkzrqnoyucZtbgoguc",
              "price" : 18
            } ignoring keys id
            Eventually block with maxDuration = 10 seconds and interval = 10 milliseconds
               When I GET /products-search (0 millis)
               Then assert status is '200' (0 millis)
               And assert response body array contains
               {
                 "name" : "mrhwfmp",
                 "description" : "hBvcqpyatdyjccokfvXtbppejlSfnuGgmahAclfjCqmzCmdqydNsiYlICpZnwgqxzVkzrqnoyucZtbgoguc",
                 "price" : 18
               } *** FAILED ***
               expected array to contain
               '{
                 "name" : "mrhwfmp",
                 "description" : "hBvcqpyatdyjccokfvXtbppejlSfnuGgmahAclfjCqmzCmdqydNsiYlICpZnwgqxzVkzrqnoyucZtbgoguc",
                 "price" : 18
               }'
               but it is not the case with array:
               [
                 {
                   "id" : "acd3425c-08cb-4cb0-be11-1d3e4611c1fa",
                   "name" : "kskqbczgcepmzvoybdKmpniflncdWqbqejtLcj",
                   "description" : "krrzzpmbwLyxsfiwxhdlnoycnfk",
                   "price" : 77
                 }
               ]
               When I GET /products-search (1 millis)
               Then assert status is '200' (0 millis)
               And assert response body array contains (0 millis)
               {
                 "name" : "mrhwfmp",
                 "description" : "hBvcqpyatdyjccokfvXtbppejlSfnuGgmahAclfjCqmzCmdqydNsiYlICpZnwgqxzVkzrqnoyucZtbgoguc",
                 "price" : 18
               }
            Eventually block succeeded after '463' retries with '1' distinct errors (6001 millis)
         Run #1 - Max transitions number per run reached
      Check block succeeded (42662 millis)
```

We can see that we have been interacting with the `CRUD` API using randomly generated `ProductDraft` and that the eventually consistent contracts seem to hold.

The source for the test and the server are available [here](https://github.com/agourlay/cornichon/tree/master/cornichon-test-framework/src/test/scala/com/github/agourlay/cornichon/framework/examples/propertyCheck/webShop).

## Caveats

- all `properties` must have the same types within a `model` definition
- the API has a few rough edges, especially regarding type inference for the `modelRunner` definition
- the max number of `generators` is hard-coded to 6