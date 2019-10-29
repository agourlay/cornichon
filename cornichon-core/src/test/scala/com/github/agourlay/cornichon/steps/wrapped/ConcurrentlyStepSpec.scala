package com.github.agourlay.cornichon.steps.wrapped

import java.util.concurrent.atomic.AtomicInteger

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import com.github.agourlay.cornichon.util.ScenarioMatchers
import org.scalatest.{ AsyncWordSpec, Matchers }

import scala.concurrent.duration._

class ConcurrentlyStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec with ScenarioMatchers {

  "ConcurrentlyStep" must {
    "fail if 'Concurrently' block contains a failed step" in {
      val nested = AssertStep(
        "always fails",
        _ => GenericEqualityAssertion(true, false)
      ) :: Nil
      val steps = ConcurrentlyStep(nested, 200.millis) :: Nil
      val s = Scenario("with Concurrently", steps)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map { res =>
        scenarioFailsWithMessage(res) {
          """Scenario 'with Concurrently' failed:
            |
            |at step:
            |always fails
            |
            |with error(s):
            |expected result was:
            |'true'
            |but actual result is:
            |'false'
            |
            |seed for the run was '1'
            |""".stripMargin
        }
      }
    }

    "fail if 'Concurrently' block does not complete within 'maxDuration because of a single step duration" in {
      val nested = AssertStep(
        "always succeed after 1000 ms",
        _ => {
          Thread.sleep(1000)
          GenericEqualityAssertion(true, true)
        }
      ) :: Nil
      val steps = ConcurrentlyStep(nested, 200.millis) :: Nil
      val s = Scenario("with Concurrently", steps)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map { res =>
        scenarioFailsWithMessage(res) {
          """Scenario 'with Concurrently' failed:
            |
            |at step:
            |Concurrently block with maxTime '200 milliseconds'
            |
            |with error(s):
            |Concurrently block did not reach completion in time: 0/1 finished
            |
            |seed for the run was '1'
            |""".stripMargin
        }
      }
    }

    "run nested block 'n' times" in {
      val uglyCounter = new AtomicInteger(0)
      val loop = 5
      val nested = AssertStep(
        "increment captured counter",
        _ => {
          uglyCounter.incrementAndGet()
          GenericEqualityAssertion(true, true)
        }
      )
      val concurrentlyStep = ConcurrentlyStep(List.fill(loop)(nested), 300.millis)
      val s = Scenario("scenario with Concurrently", concurrentlyStep :: Nil)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map { res =>
        res.isSuccess should be(true)
        uglyCounter.intValue() should be(loop)
      }
    }
  }

}
