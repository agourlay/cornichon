package com.github.agourlay.cornichon.core

import java.util.Timer

import com.github.agourlay.cornichon.resolver.Resolver
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericAssertion }
import com.github.agourlay.cornichon.util.ShowInstances
import org.scalatest.{ AsyncWordSpec, Matchers }

import scala.concurrent.ExecutionContext

class EngineSpec extends AsyncWordSpec with Matchers with ShowInstances {

  implicit val timer = new Timer()
  val resolver = Resolver.withoutExtractor()
  val engine = Engine.withStepTitleResolver(resolver, ExecutionContext.global)

  "An engine" when {
    "runScenario" must {
      "executes all steps of a scenario" in {
        val session = Session.newEmpty
        val steps = Vector(AssertStep[Int]("first step", s ⇒ GenericAssertion(2 + 1, 3)))
        val s = Scenario("test", steps)
        engine.runScenario(session)(s).map(_.isSuccess should be(true))
      }

      "stops at first failed step" in {
        val session = Session.newEmpty
        val step1 = AssertStep[Int]("first step", s ⇒ GenericAssertion(2, 2))
        val step2 = AssertStep[Int]("second step", s ⇒ GenericAssertion(4, 5))
        val step3 = AssertStep[Int]("third step", s ⇒ GenericAssertion(1, 1))
        val steps = Vector(
          step1, step2, step3
        )
        val s = Scenario("test", steps)
        engine.runScenario(session)(s).map { res ⇒
          withClue(s"logs were ${res.logs}") {
            res match {
              case s: SuccessScenarioReport ⇒ fail("Should be a FailedScenarioReport")
              case f: FailureScenarioReport ⇒
                f.failedSteps.head.error.msg should be(
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
        val mainStep = AssertStep[Boolean]("main step", s ⇒ GenericAssertion(true, false))
        val finallyStep = AssertStep[Boolean]("finally step", s ⇒ GenericAssertion(true, false))
        val s = Scenario("test", Vector(mainStep))
        engine.runScenario(session, Vector(finallyStep))(s).map {
          case s: SuccessScenarioReport ⇒ fail(s"Should be a FailedScenarioReport and not success with\n${s.logs}")
          case f: FailureScenarioReport ⇒
            withClue(f.msg) {
              f.msg should be(
                """Scenario 'test' failed:
                |
                |at step:
                |main step
                |
                |with error:
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
                |with error:
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
