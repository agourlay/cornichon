package com.github.agourlay.cornichon.steps.wrapped

import java.util.concurrent.atomic.AtomicInteger
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import munit.FunSuite

import scala.concurrent.duration._

class RepeatConcurrentlyStepSpec extends FunSuite with CommonTestSuite {

  test("fails if 'repeatConcurrently' block contains a failed step") {
    val nested = AssertStep(
      "always fails",
      _ => GenericEqualityAssertion(true, false)
    ) :: Nil
    val steps = RepeatConcurrentlyStep(times = 2, nested, parallelism = 1, maxTime = 300.millis) :: Nil
    val s = Scenario("with RepeatConcurrently", steps)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    scenarioFailsWithMessage(res) {
      """Scenario 'with RepeatConcurrently' failed:
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

  test("fails if 'RepeatConcurrently' block does not complete within 'maxDuration because of a single step duration") {
    val nested = AssertStep(
      "always succeed after 100 ms",
      _ => {
        Thread.sleep(100)
        GenericEqualityAssertion(true, true)
      }
    ) :: Nil
    val steps = RepeatConcurrentlyStep(times = 1, nested, parallelism = 1, maxTime = 20.millis) :: Nil
    val s = Scenario("with RepeatConcurrently", steps)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    scenarioFailsWithMessage(res) {
      """Scenario 'with RepeatConcurrently' failed:
          |
          |at step:
          |Repeat concurrently block '1' times with parallel factor '1' and maxTime '20 milliseconds'
          |
          |with error(s):
          |Repeat concurrently block did not reach completion in time: 0/1 finished
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
    ) :: Nil
    val concurrentlyStep = RepeatConcurrentlyStep(times = loop, nested, parallelism = 2, 300.millis)
    val s = Scenario("scenario with RepeatConcurrently", concurrentlyStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    assert(uglyCounter.intValue() == loop)
  }

  // feature missing
  //    test("merges all session from 'RepeatConcurrent' runs") {
  //      val steps = Range.inclusive(1, 5).map { i =>
  //        EffectStep.fromSyncE(
  //          title = s"set $i in the session",
  //          effect = _.session.addValue("index", i.toString)
  //        )
  //      }
  //      val repeatFactor = 5
  //      val concurrentlyStep = RepeatConcurrentlyStep(times = repeatFactor, steps.toList, repeatFactor, 300.millis)
  //      val s = Scenario("scenario with RepeatConcurrently", concurrentlyStep :: Nil)
  //      val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
  //      assert(res.isSuccess)
  //      assert(res.session.getHistory("index").valueUnsafe == Vector.fill(repeatFactor)(Vector("1", "2", "3", "4", "5")).flatten)
  //    }
}
