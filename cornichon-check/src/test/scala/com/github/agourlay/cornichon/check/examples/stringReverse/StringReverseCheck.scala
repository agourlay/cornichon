package com.github.agourlay.cornichon.check.examples.stringReverse

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.check._
import com.github.agourlay.cornichon.check.examples.HttpServer

import scala.concurrent.Await
import scala.concurrent.duration._

class StringReverseCheck extends CornichonFeature with CheckDsl {

  def feature = Feature("Basic examples of checks") {

    Scenario("reverse a string twice yields the same results") {

      Given I check_model(maxNumberOfRuns = 5, maxNumberOfTransitions = 1)(doubleReverseModel)

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

  private val generateStringAction = Property1[String](
    description = "generate and save string",
    preCondition = session_value("random-input").isAbsent,
    invariant = stringGenerator ⇒ Attach {
      Given I save("random-input" -> stringGenerator())
    })

  private val reverseStringAction = Property1[String](
    description = "reverse a string twice yields the same value",
    preCondition = session_value("random-input").isPresent,
    invariant = _ ⇒ Attach {
      Given I post("/double-reverse").withParams("word" -> "<random-input>")
      Then assert status.is(200)
      Then assert body.is("<random-input>")
    })

  val doubleReverseModel = ModelRunner.make[String](stringGen)(
    Model(
      description = "reversing a string twice yields same value",
      entryPoint = generateStringAction,
      transitions = Map(
        generateStringAction -> ((1.0, reverseStringAction) :: Nil)
      )
    )
  )
}
