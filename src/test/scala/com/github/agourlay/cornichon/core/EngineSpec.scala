package com.github.agourlay.cornichon.core

import java.util.Timer

import com.github.agourlay.cornichon.resolver.Resolver
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import com.github.agourlay.cornichon.util.Instances
import org.scalatest.{ AsyncWordSpec, Matchers }

import scala.concurrent.ExecutionContext

class EngineSpec extends AsyncWordSpec with Matchers with Instances {

  implicit val timer = new Timer()
  val resolver = Resolver.withoutExtractor()
  val engine = Engine.withStepTitleResolver(resolver, ExecutionContext.global)

  "An engine" when {
    "runScenario" must {
      "executes all steps of a scenario" in {
        val session = Session.newEmpty
        val steps = AssertStep("first step", s ⇒ GenericEqualityAssertion(2 + 1, 3)) :: Nil
        val s = Scenario("test", steps)
        engine.runScenario(session)(s).map(_.isSuccess should be(true))
      }

      "stops at first failed step" in {
        val session = Session.newEmpty
        val step1 = AssertStep("first step", s ⇒ GenericEqualityAssertion(2, 2))
        val step2 = AssertStep("second step", s ⇒ GenericEqualityAssertion(4, 5))
        val step3 = AssertStep("third step", s ⇒ GenericEqualityAssertion(1, 1))
        val steps = step1 :: step2 :: step3 :: Nil
        val s = Scenario("test", steps)
        engine.runScenario(session)(s).map { res ⇒
          withClue(s"logs were ${res.logs}") {
            res match {
              case s: SuccessScenarioReport ⇒ fail("Should be a FailedScenarioReport")
              case f: FailureScenarioReport ⇒
                f.failedSteps.head.errors.head.renderedMessage should be(
                  """
                  |expected result was:
                  |'4'
                  |but actual result is:
                  |'5'""".
                  stripMargin.trim
                )
            }
          }
        }
      }

      "accumulates errors if 'main' and 'finally' fail" in {
        val session = Session.newEmpty
        val mainStep = AssertStep("main step", s ⇒ GenericEqualityAssertion(true, false))
        val finallyStep = AssertStep("finally step", s ⇒ GenericEqualityAssertion(true, false))
        val s = Scenario("test", mainStep :: Nil)
        engine.runScenario(session, finallyStep :: Nil)(s).map {
          case s: SuccessScenarioReport ⇒ fail(s"Should be a FailedScenarioReport and not success with\n${s.logs}")
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
        }
      }
    }
  }
}
