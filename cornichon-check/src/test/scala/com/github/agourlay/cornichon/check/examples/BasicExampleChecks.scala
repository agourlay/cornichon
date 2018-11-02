package com.github.agourlay.cornichon.check.examples

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.check._
import com.github.agourlay.cornichon.steps.regular.EffectStep

import scala.concurrent.Await
import scala.concurrent.duration._

class BasicExampleChecks extends CornichonFeature with CheckDsl {

  def feature = Feature("Basic examples of checks") {

    Scenario("reverse a string twice yields the same results") {

      Given I check_model(maxNumberOfRuns = 5, maxNumberOfTransitions = 1)(doubleReverseModel)

    }
  }

  lazy val port = 8080

  // Base url used for all HTTP steps
  override lazy val baseUrl = s"http://localhost:$port"

  var server: HttpServer = _

  // Starts up test server
  beforeFeature {
    server = Await.result(new ReverseAPI().start(port), 5.second)
  }

  // Stops test server
  afterFeature {
    Await.result(server.shutdown(), 5.second)
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
    description = "reverse a string twice yields the same value",
    preConditions = session_value(randomInputKey).isPresent :: Nil,
    effect = _ ⇒ Attach {
      Given I post("/double-reverse").withParams("word" -> "<random-input>")
      Then assert status.is(200)
      And I save_body(doubleReversedKey)
    },
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
