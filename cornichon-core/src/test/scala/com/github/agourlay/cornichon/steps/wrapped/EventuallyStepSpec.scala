package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }
import org.scalatest.{ AsyncWordSpec, Matchers }

import scala.concurrent.duration._

class EventuallyStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "EventuallyStep" must {
    "replay eventually wrapped steps" in {
      val eventuallyConf = EventuallyConf(maxTime = 5.seconds, interval = 10.milliseconds)
      val nested = AssertStep(
        "possible random value step",
        _ ⇒ GenericEqualityAssertion(scala.util.Random.nextInt(10), 5)
      ) :: Nil

      val steps = EventuallyStep(nested, eventuallyConf, oscillationAllowed = true) :: Nil
      val s = Scenario("scenario with eventually", steps)
      engine.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(true))
    }

    "replay eventually wrapped steps until limit" in {
      val eventuallyConf = EventuallyConf(maxTime = 1.seconds, interval = 100.milliseconds)
      var counter = 0
      val nested = AssertStep(
        "impossible random value step", _ ⇒ {
          counter = counter + 1
          Assertion.failWith("nop!")
        }
      ) :: Nil
      val eventuallyStep = EventuallyStep(nested, eventuallyConf, oscillationAllowed = true)
      val s = Scenario("scenario with eventually that fails", eventuallyStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map { r ⇒
        r.isSuccess should be(false)
        counter <= 10 should be(true) // at most 10*100millis
      }
    }

    "replay eventually handle hanging wrapped steps" in {
      val eventuallyConf = EventuallyConf(maxTime = 1.seconds, interval = 100.milliseconds)
      val nested = AssertStep(
        "slow always true step", _ ⇒ {
          Thread.sleep(100000)
          Assertion.alwaysValid
        }
      ) :: Nil
      val eventuallyStep = EventuallyStep(nested, eventuallyConf, oscillationAllowed = true)
      val s = Scenario("scenario with eventually that fails", eventuallyStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map {
        case f: FailureScenarioReport ⇒
          f.isSuccess should be(false)
          f.msg should be("""Scenario 'scenario with eventually that fails' failed:
                            |
                            |at step:
                            |Eventually block with maxDuration = 1 second and interval = 100 milliseconds
                            |
                            |with error(s):
                            |Eventually block is interrupted due to a long period of inactivity
                            |""".stripMargin)
        case other @ _ ⇒
          fail(s"should have failed but got $other")
      }
    }

    "report distinct errors in the logs and only final error in the report" in {
      val eventuallyConf = EventuallyConf(maxTime = 1.seconds, interval = 100.milliseconds)
      var counter = 0
      val nested = AssertStep(
        "Fail differently", _ ⇒ {
          if (counter == 0 || counter == 1 || counter == 2) {
            counter += 1
            Assertion.failWith(s"Failing $counter")
          } else
            Assertion.failWith("Failing forever")
        }
      ) :: Nil
      val eventuallyStep = EventuallyStep(nested, eventuallyConf, oscillationAllowed = true)
      val s = Scenario("scenario with different failures", eventuallyStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map {
        case f: FailureScenarioReport ⇒
          f.isSuccess should be(false)
          f.msg should be("""Scenario 'scenario with different failures' failed:
                            |
                            |at step:
                            |Fail differently
                            |
                            |with error(s):
                            |Failing forever
                            |""".stripMargin)
          val logs = LogInstruction.renderLogs(f.logs.drop(2).dropRight(1), colorized = false)
          logs should be("""
                           |      Eventually block with maxDuration = 1 second and interval = 100 milliseconds
                           |         Fail differently *** FAILED ***
                           |         Failing 1
                           |         Fail differently *** FAILED ***
                           |         Failing 2
                           |         Fail differently *** FAILED ***
                           |         Failing 3
                           |         Fail differently *** FAILED ***
                           |         Failing forever
                           |""".stripMargin)
        case other @ _ ⇒
          fail(s"should have failed but got $other")
      }
    }

    "detect oscillation in wrapped step" in {
      val eventuallyConf = EventuallyConf(maxTime = 1.seconds, interval = 100.milliseconds)
      var counter = 1
      val nested = AssertStep(
        "Fail with oscillation", _ ⇒ {
          if (counter == 1) {
            counter += 1
            Assertion.failWith(s"Failure mode one")
          } else if (counter == 2) {
            counter += 1
            Assertion.failWith(s"Failure mode two")
          } else
            Assertion.failWith("Failure mode one")
        }
      ) :: Nil
      val eventuallyStep = EventuallyStep(nested, eventuallyConf, oscillationAllowed = false)
      val s = Scenario("scenario with different failures", eventuallyStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map {
        case f: FailureScenarioReport ⇒
          f.isSuccess should be(false)
          f.msg should be("""Scenario 'scenario with different failures' failed:
                            |
                            |at step:
                            |Eventually block with maxDuration = 1 second and interval = 100 milliseconds
                            |
                            |with error(s):
                            |Eventually block failed because it detected an oscillation of errors
                            |
                            |at step:
                            |Fail with oscillation
                            |
                            |with error(s):
                            |Failure mode one
                            |
                            |""".stripMargin)
          val logs = LogInstruction.renderLogs(f.logs.drop(2).dropRight(1), colorized = false)
          logs should be("""
                           |      Eventually block with maxDuration = 1 second and interval = 100 milliseconds
                           |         Fail with oscillation *** FAILED ***
                           |         Failure mode one
                           |         Fail with oscillation *** FAILED ***
                           |         Failure mode two
                           |         Fail with oscillation *** FAILED ***
                           |         Failure mode one
                           |""".stripMargin)
        case other @ _ ⇒
          fail(s"should have failed but got $other")
      }
    }

  }

}
