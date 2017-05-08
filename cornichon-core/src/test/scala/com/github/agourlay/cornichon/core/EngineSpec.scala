package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.dsl.ProvidedInstances._
import com.github.agourlay.cornichon.resolver.Resolver
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }

import monix.execution.Scheduler

import org.scalatest.{ AsyncWordSpec, Matchers }

import scala.concurrent.ExecutionContext

class EngineSpec extends AsyncWordSpec with Matchers {

  implicit val scheduler = Scheduler(ExecutionContext.global)
  val engine = Engine.withStepTitleResolver(Resolver.withoutExtractor())

  "An engine" when {
    "runScenario" must {
      "executes all steps of a scenario" in {
        val steps = AssertStep("first step", s ⇒ GenericEqualityAssertion(2 + 1, 3)) :: Nil
        val s = Scenario("test", steps)
        engine.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(true))
      }

      "stops at first failed step" in {
        val step1 = AssertStep("first step", s ⇒ GenericEqualityAssertion(2, 2))
        val step2 = AssertStep("second step", s ⇒ GenericEqualityAssertion(4, 5))
        val step3 = AssertStep("third step", s ⇒ GenericEqualityAssertion(1, 1))
        val steps = step1 :: step2 :: step3 :: Nil
        val s = Scenario("test", steps)
        engine.runScenario(Session.newEmpty)(s).map { res ⇒
          withClue(s"logs were ${res.logs}") {
            res match {
              case f: FailureScenarioReport ⇒
                f.failedSteps.head.errors.head.renderedMessage should be(
                  """
                  |expected result was:
                  |'4'
                  |but actual result is:
                  |'5'"""
                  .stripMargin.trim
                )
              case _ ⇒ fail(s"Should be a FailedScenarioReport but got \n${res.logs}")
            }
          }
        }
      }

      "accumulates errors if 'main' and 'finally' fail" in {
        val mainStep = AssertStep("main step", s ⇒ GenericEqualityAssertion(true, false))
        val finallyStep = AssertStep("finally step", s ⇒ GenericEqualityAssertion(true, false))
        val s = Scenario("test", mainStep :: Nil)
        engine.runScenario(Session.newEmpty, finallyStep :: Nil)(s).map {
          case f: FailureScenarioReport ⇒
            withClue(f.msg) {
              f.msg should be(
                """Scenario 'test' failed:
                |
                |at step:
                |main step
                |
                |with error(s):
                |expected result was:
                |'true'
                |but actual result is:
                |'false'
                |
                |and
                |
                |at step:
                |finally step
                |
                |with error(s):
                |expected result was:
                |'true'
                |but actual result is:
                |'false'
                |""".
                stripMargin
              )
            }
          case other ⇒ fail(s"Should be a FailedScenarioReport but got \n${other.logs}")
        }
      }
    }
  }
}
