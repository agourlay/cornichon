package com.github.agourlay.cornichon.check.examples.turnstile

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.check._
import com.github.agourlay.cornichon.check.checkModel.{ Model, ModelRunner, Property0 }
import com.github.agourlay.cornichon.check.examples.HttpServer

import scala.concurrent.Await
import scala.concurrent.duration._

class TurnstileCheck extends CornichonFeature with CheckDsl {

  def feature = Feature("Basic examples of checks") {

    Scenario("Turnstile acts according to model") {

      Given I check_model(maxNumberOfRuns = 1, maxNumberOfTransitions = 10)(turnstileModel)

    }
  }

  lazy val port = 8080

  // Base url used for all HTTP steps
  override lazy val baseUrl = s"http://localhost:$port"

  //Travis CI struggles with default value `2.seconds`
  override lazy val requestTimeout = 5.second

  var server: HttpServer = _

  // Starts up test server
  beforeFeature {
    server = Await.result(new TurnstileAPI().start(port), 5.second)
  }

  // Stops test server
  afterFeature {
    Await.result(server.shutdown(), 5.second)
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
