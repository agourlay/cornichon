package com.github.agourlay.cornichon.steps.wrapped

import java.util.concurrent.atomic.AtomicInteger

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import org.scalatest.{ AsyncWordSpec, Matchers }

import scala.concurrent.duration._

class RepeatConcurrentlyStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "RepeatConcurrentlyStep" must {
    "fail if 'repeatConcurrently' block contains a failed step" in {
      val nested = AssertStep(
        "always fails",
        _ ⇒ GenericEqualityAssertion(true, false)
      ) :: Nil
      val steps = RepeatConcurrentlyStep(times = 3, nested, parallelism = 1, 200.millis) :: Nil
      val s = Scenario("scenario with RepeatConcurrently", steps)
      engine.runScenario(Session.newEmpty)(s).map {
        case f: FailureScenarioReport ⇒
          f.failedSteps.head.errors.head.renderedMessage should be("expected result was:\n'true'\nbut actual result is:\n'false'")
        case _ ⇒ assert(false)
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
      val steps = RepeatConcurrentlyStep(times = 1, nested, parallelism = 1, 200.millis) :: Nil
      val s = Scenario("scenario with RepeatConcurrently", steps)
      engine.runScenario(Session.newEmpty)(s).map {
        case f: FailureScenarioReport ⇒ f.failedSteps.head.errors.head.renderedMessage should be("Repeat concurrently block did not reach completion in time: 0/1 finished")
        case _                        ⇒ assert(false)
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
      engine.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        uglyCounter.intValue() should be(loop)
      }
    }

    "merge all session from 'RepeatConcurrent' runs" ignore {
      val steps = Range.inclusive(1, 5).map { i ⇒
        EffectStep.fromSyncE(
          title = s"set $i in the session",
          effect = _.addValue("indice", i.toString)
        )
      }
      val repeatFactor = 5
      val concurrentlyStep = RepeatConcurrentlyStep(times = repeatFactor, steps.toList, repeatFactor, 300.millis)
      val s = Scenario("scenario with RepeatConcurrently", concurrentlyStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        res.session.getHistory("indice").valueUnsafe should be(Vector.fill(repeatFactor)(Vector("1", "2", "3", "4", "5")).flatten)

      }
    }
  }

}
