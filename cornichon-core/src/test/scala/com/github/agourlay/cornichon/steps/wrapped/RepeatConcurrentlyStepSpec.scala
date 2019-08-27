package com.github.agourlay.cornichon.steps.wrapped

import java.util.concurrent.atomic.AtomicInteger

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import com.github.agourlay.cornichon.util.ScenarioMatchers
import org.scalatest.{ AsyncWordSpec, Matchers }

import scala.concurrent.duration._

class RepeatConcurrentlyStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec with ScenarioMatchers {

  "RepeatConcurrentlyStep" must {
    "fail if 'repeatConcurrently' block contains a failed step" in {
      val nested = AssertStep(
        "always fails",
        _ ⇒ GenericEqualityAssertion(true, false)
      ) :: Nil
      val steps = RepeatConcurrentlyStep(times = 3, nested, parallelism = 1, maxTime = 300.millis) :: Nil
      val s = Scenario("with RepeatConcurrently", steps)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map { res ⇒
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
    }

    "fail if 'RepeatConcurrently' block does not complete within 'maxDuration because of a single step duration" in {
      val nested = AssertStep(
        "always succeed after 1000 ms",
        _ ⇒ {
          Thread.sleep(1000)
          GenericEqualityAssertion(true, true)
        }
      ) :: Nil
      val steps = RepeatConcurrentlyStep(times = 1, nested, parallelism = 1, maxTime = 100.millis) :: Nil
      val s = Scenario("with RepeatConcurrently", steps)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map { res ⇒
        scenarioFailsWithMessage(res) {
          """Scenario 'with RepeatConcurrently' failed:
            |
            |at step:
            |Repeat concurrently block '1' times with parallel factor '1' and maxTime '100 milliseconds'
            |
            |with error(s):
            |Repeat concurrently block did not reach completion in time: 0/1 finished
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
        _ ⇒ {
          uglyCounter.incrementAndGet()
          GenericEqualityAssertion(true, true)
        }
      ) :: Nil
      val concurrentlyStep = RepeatConcurrentlyStep(times = loop, nested, parallelism = 2, 300.millis)
      val s = Scenario("scenario with RepeatConcurrently", concurrentlyStep :: Nil)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        uglyCounter.intValue() should be(loop)
      }
    }

    "merge all session from 'RepeatConcurrent' runs" ignore {
      val steps = Range.inclusive(1, 5).map { i ⇒
        EffectStep.fromSyncE(
          title = s"set $i in the session",
          effect = _.session.addValue("index", i.toString)
        )
      }
      val repeatFactor = 5
      val concurrentlyStep = RepeatConcurrentlyStep(times = repeatFactor, steps.toList, repeatFactor, 300.millis)
      val s = Scenario("scenario with RepeatConcurrently", concurrentlyStep :: Nil)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        res.session.getHistory("index").valueUnsafe should be(Vector.fill(repeatFactor)(Vector("1", "2", "3", "4", "5")).flatten)
      }
    }
  }

}
