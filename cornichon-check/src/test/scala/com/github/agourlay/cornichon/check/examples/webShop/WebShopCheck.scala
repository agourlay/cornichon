package com.github.agourlay.cornichon.check.examples.webShop

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.check._
import com.github.agourlay.cornichon.check.checkModel.{ Model, ModelRunner, Property1 }
import com.github.agourlay.cornichon.check.examples.HttpServer
import io.circe.syntax._
import io.circe.generic.auto._
import org.scalacheck.Gen
import org.scalacheck.rng.Seed

import scala.concurrent.duration._
import scala.concurrent.Await

class WebShopCheck extends CornichonFeature with CheckDsl {

  def feature = Feature("Advanced example of model checks") {

    Scenario("WebShop acts according to model") {

      Given I check_model(maxNumberOfRuns = 1, maxNumberOfTransitions = 100)(webShopModel)

    }
  }

  lazy val port = 8080

  // Base url used for all HTTP steps
  override lazy val baseUrl = s"http://localhost:$port"

  //Travis CI struggles with default value `2.seconds`
  override lazy val requestTimeout = 5.second

  val maxIndexSyncTimeout = 1.seconds

  var server: HttpServer = _

  // Starts up test server
  beforeFeature {
    server = Await.result(new WebShopAPI(maxIndexSyncTimeout).start(port), 5.second)
  }

  // Stops test server
  afterFeature {
    Await.result(server.shutdown(), 5.second)
  }

  def productDraftGen(rc: RandomContext): Generator[ProductDraft] = OptionalValueGenerator(
    name = "a product draft",
    gen = () ⇒ {
      val nextSeed = rc.seededRandom.nextLong()
      val params = Gen.Parameters.default.withInitialSeed(nextSeed)
      val gen =
        for {
          name ← Gen.alphaStr
          description ← Gen.alphaStr
          price ← Gen.posNum[Int]
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
