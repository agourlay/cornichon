package com.github.agourlay.cornichon.steps.wrapped

import java.util.concurrent.atomic.AtomicInteger

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import com.github.agourlay.cornichon.testHelpers.CommonSpec
import utest._

import scala.concurrent.duration._

object ConcurrentlyStepSpec extends TestSuite with CommonSpec {

  val tests = Tests {
    test("fails if 'Concurrently' block contains a failed step") {
      val nested = AssertStep(
        "always fails",
        _ => GenericEqualityAssertion(true, false)
      ) :: Nil
      val steps = ConcurrentlyStep(nested, 200.millis) :: Nil
      val s = Scenario("with Concurrently", steps)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
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

    test("fails if 'Concurrently' block does not complete within 'maxDuration because of a single step duration") {
      val nested = AssertStep(
        "always succeed after 500 ms",
        _ => {
          Thread.sleep(500)
          GenericEqualityAssertion(true, true)
        }
      ) :: Nil
      val steps = ConcurrentlyStep(nested, 200.millis) :: Nil
      val s = Scenario("with Concurrently", steps)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
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

    test("runs nested block 'n' times") {
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
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(res.isSuccess)
      assert(uglyCounter.intValue() == loop)
    }
  }
}
