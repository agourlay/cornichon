package com.github.agourlay.cornichon.steps.wrapped

import cats.instances.boolean._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import org.scalatest.{ Matchers, AsyncWordSpec }

import scala.concurrent.duration._

class RepeatDuringStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "RepeatDuringStep" must {
    "fail if 'repeatDuring' block contains a failed step" in {
      val nested = AssertStep(
        "always fails",
        s ⇒ GenericEqualityAssertion(true, false)
      ) :: Nil
      val repeatDuring = RepeatDuringStep(nested, 5.millis)
      val s = Scenario("scenario with RepeatDuring", repeatDuring :: Nil)
      engine.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
    }

    "repeat steps inside 'repeatDuring' for at least the duration param" in {
      val nested = AssertStep(
        "always valid",
        s ⇒ {
          Thread.sleep(1)
          GenericEqualityAssertion(true, true)
        }
      ) :: Nil
      val repeatDurinStep = RepeatDuringStep(nested, 50.millis)
      val s = Scenario("scenario with RepeatDuring", repeatDurinStep :: Nil)
      val now = System.nanoTime
      engine.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        withClue(executionTime.toMillis) {
          executionTime.gt(50.millis) should be(true)
          // empiric values for the upper bound here
          executionTime.lt(60.millis) should be(true)
        }
      }
    }

    "repeat steps inside 'repeatDuring' at least once if they take more time than the duration param" in {
      val nested = AssertStep(
        "always valid",
        s ⇒ {
          Thread.sleep(500)
          GenericEqualityAssertion(true, true)
        }
      ) :: Nil
      val repeatDuringStep = RepeatDuringStep(nested, 50.millis)
      val s = Scenario("scenario with RepeatDuring", repeatDuringStep :: Nil)
      val now = System.nanoTime
      engine.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        withClue(executionTime.toMillis) {
          executionTime.gt(50.millis) should be(true)
          // empiric values for the upper bound here
          executionTime.lt(550.millis) should be(true)
        }
      }
    }
  }

}
