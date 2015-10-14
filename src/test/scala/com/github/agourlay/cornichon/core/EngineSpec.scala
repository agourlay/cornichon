package com.github.agourlay.cornichon.core

import org.scalatest.{ Matchers, WordSpec }
import scala.concurrent.duration._

class EngineSpec extends WordSpec with Matchers {

  val engine = new Engine()

  "An engine" must {
    "execute all steps of a scenario" in {
      val session = Session.newSession
      val steps = Seq(ExecutableStep[Int]("first step", s ⇒ (s, SimpleStepAssertion(2 + 1, 3))))
      val s = Scenario("test", steps)
      engine.runScenario(s)(session).isInstanceOf[SuccessScenarioReport] should be(true)
    }

    "fail if instruction throws exception" in {
      val session = Session.newSession
      val steps = Seq(
        ExecutableStep[Int]("stupid step", s ⇒ {
          6 / 0
          (s, SimpleStepAssertion(2, 2))
        })
      )
      val s = Scenario("scenario with stupid test", steps)
      engine.runScenario(s)(session).isInstanceOf[FailedScenarioReport] should be(true)
    }

    "stop at first failed step" in {
      val session = Session.newSession
      val step1 = ExecutableStep[Int]("first step", s ⇒ (s, SimpleStepAssertion(2, 2)))
      val step2 = ExecutableStep[Int]("second step", s ⇒ (s, SimpleStepAssertion(4, 5)))
      val step3 = ExecutableStep[Int]("third step", s ⇒ (s, SimpleStepAssertion(1, 1)))
      val steps = Seq(
        step1, step2, step3
      )
      val s = Scenario("test", steps)
      engine.runScenario(s)(session) match {
        case s: SuccessScenarioReport ⇒ fail("Should be a FailedScenarioReport")
        case f: FailedScenarioReport ⇒
          f.failedStep.error.msg should be("""
           |expected result was:
           |'4'
           |but actual result is:
           |'5'""".stripMargin.trim)
          f.successSteps should be(Seq(step1.title))
          f.notExecutedStep should be(Seq(step3.title))
      }
    }

    "replay eventually wrapped steps" in {
      val session = Session.newSession
      val eventuallyConf = EventuallyConf(maxTime = 5.seconds, interval = 100.milliseconds)
      val steps = Seq(
        EventuallyStart(eventuallyConf),
        ExecutableStep("random value step", s ⇒ {
          (s, SimpleStepAssertion(scala.util.Random.nextInt(10), 5))
        }),
        EventuallyStop(eventuallyConf)
      )
      val s = Scenario("scenario with eventually", steps)
      engine.runScenario(s)(session).isInstanceOf[SuccessScenarioReport] should be(true)
    }

    "replay eventually wrapped steps until limit" in {
      val session = Session.newSession
      val eventuallyConf = EventuallyConf(maxTime = 1.seconds, interval = 100.milliseconds)
      val steps = Seq(
        EventuallyStart(eventuallyConf),
        ExecutableStep("random value step", s ⇒ {
          (s, SimpleStepAssertion(scala.util.Random.nextInt(10), 11))
        }),
        EventuallyStop(eventuallyConf)
      )
      val s = Scenario("scenario with eventually that fails", steps)
      engine.runScenario(s)(session).isInstanceOf[FailedScenarioReport] should be(true)
    }

    "success if non equality was expected" in {
      val session = Session.newSession
      val steps = Seq(
        ExecutableStep("non equals step", s ⇒ {
          (s, SimpleStepAssertion(1, 2))
        }, negate = true)
      )
      val s = Scenario("scenario with unresolved", steps)
      engine.runScenario(s)(session).isInstanceOf[SuccessScenarioReport] should be(true)
    }
  }

}
