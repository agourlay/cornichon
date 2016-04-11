package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.steps.regular.AssertStep
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.ExecutionContext

class EngineSpec extends WordSpec with Matchers {

  val engine = new Engine(ExecutionContext.global)

  "An engine" when {
    "runScenario" must {
      "execute all steps of a scenario" in {
        val session = Session.newSession
        val steps = Vector(AssertStep[Int]("first step", s ⇒ SimpleStepAssertion(2 + 1, 3)))
        val s = Scenario("test", steps)
        engine.runScenario(session)(s).stepsRunReport.isSuccess should be(true)
      }

      "stop at first failed step" in {
        val session = Session.newSession
        val step1 = AssertStep[Int]("first step", s ⇒ SimpleStepAssertion(2, 2))
        val step2 = AssertStep[Int]("second step", s ⇒ SimpleStepAssertion(4, 5))
        val step3 = AssertStep[Int]("third step", s ⇒ SimpleStepAssertion(1, 1))
        val steps = Vector(
          step1, step2, step3
        )
        val s = Scenario("test", steps)
        val res = engine.runScenario(session)(s).stepsRunReport
        withClue(s"logs were ${res.logs}") {
          res match {
            case s: SuccessRunSteps ⇒ fail("Should be a FailedScenarioReport")
            case f: FailedRunSteps ⇒
              f.error.msg.replaceAll("\r", "") should be("""
              |expected result was:
              |'4'
              |but actual result is:
              |'5'""".stripMargin.trim)
          }
        }
      }
    }
  }
}
