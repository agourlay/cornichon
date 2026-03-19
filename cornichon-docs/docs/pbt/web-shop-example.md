{%
laika.title = Web shop example
%}

# Web shop example (advanced)

This is an advanced example of [random model exploration](random-model-exploration.md). Make sure you're familiar with the concepts and the [Turnstile example](random-model-exploration.md#turnstile) before reading this.

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
    gen = () => {
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
    invariant = _ => Attach {
      Given I get("/products")
      Then assert status.is(200)
      Then assert body.asArray.isEmpty
    }
  )

  private val createProduct = Property1[ProductDraft](
    description = "create a product",
    invariant = pd => {
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
    invariant = _ => Attach {
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
    invariant = pd => {
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

We can see that we have been interacting with the `CRUD` API using randomly generated `ProductDraft` and that the eventually consistent contracts seem to hold.

The source for the test and the server are available [here](https://github.com/agourlay/cornichon/tree/master/cornichon-test-framework/src/test/scala/com/github/agourlay/cornichon/framework/examples/propertyCheck/webShop).
