package com.github.agourlay.cornichon.check.examples.turnstile

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.check._
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

  private val pushCoinAction = Action0(
    description = "push a coin",
    preConditions = Nil,
    effect = () ⇒ Given I post("/push-coin"),
    postConditions = status.is(200) :: Nil)

  private val pushCoinBlockedAction = Action0(
    description = "push a coin is a blocked",
    preConditions = Nil,
    effect = () ⇒ Given I post("/push-coin"),
    postConditions = status.is(400) :: Nil)

  private val walkThroughOkAction = Action0(
    description = "walk through ok",
    preConditions = Nil,
    effect = () ⇒ Given I post("/walk-through"),
    postConditions = status.is(200) :: Nil)

  private val walkThroughBlockedAction = Action0(
    description = "walk through blocked",
    preConditions = Nil,
    effect = () ⇒ Given I post("/walk-through"),
    postConditions = status.is(400) :: Nil)

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
