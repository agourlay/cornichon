package com.github.agourlay.cornichon.core

import java.util.concurrent.atomic.AtomicInteger

import com.github.agourlay.cornichon.dsl.ProvidedInstances._
import com.github.agourlay.cornichon.resolver.PlaceholderResolver
import com.github.agourlay.cornichon.steps.regular.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import com.github.agourlay.cornichon.util.TaskSpec
import monix.execution.Scheduler
import org.scalatest.{ AsyncWordSpec, Matchers }

import scala.concurrent.ExecutionContext

class EngineSpec extends AsyncWordSpec with Matchers with TaskSpec {

  implicit val scheduler = Scheduler(ExecutionContext.global)
  val engine = Engine.withStepTitleResolver(PlaceholderResolver.withoutExtractor())

  "An engine" when {
    "runScenario" must {
      "executes all steps of a scenario" in {
        val steps = AssertStep("first step", _ ⇒ GenericEqualityAssertion(2 + 1, 3)) :: Nil
        val s = Scenario("test", steps)
        engine.runScenario(Session.newEmpty)(s).map { r ⇒
          r.isSuccess should be(true)
          r.logs.size should be(3)
        }
      }

      "stops at first failed step" in {
        val step1 = AssertStep("first step", _ ⇒ GenericEqualityAssertion(2, 2))
        val step2 = AssertStep("second step", _ ⇒ GenericEqualityAssertion(4, 5))
        val step3 = AssertStep("third step", _ ⇒ GenericEqualityAssertion(1, 1))
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
                f.logs.size should be(5)
              case _ ⇒ fail(s"Should be a FailedScenarioReport but got \n${res.logs}")
            }
          }
        }
      }

      "accumulates errors if 'main' and 'finally' fail" in {
        val mainStep = AssertStep("main step", _ ⇒ GenericEqualityAssertion(true, false))
        val finalAssertion = AssertStep("finally step", _ ⇒ GenericEqualityAssertion(true, false))
        val s = Scenario("test", mainStep :: Nil)
        engine.runScenario(Session.newEmpty, FeatureExecutionContext(finallySteps = finalAssertion :: Nil))(s).map {
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
              f.logs.size should be(7)
            }
          case other ⇒ fail(s"Should be a FailedScenarioReport but got \n${other.logs}")
        }
      }

      "run all valid effects" in {
        val uglyCounter = new AtomicInteger(0)
        val effectNumber = 5
        val effect = EffectStep.fromSync(
          "increment captured counter",
          s ⇒ {
            uglyCounter.incrementAndGet()
            s
          }
        )

        val s = Scenario("scenario with effects", List.fill(effectNumber)(effect))
        engine.runScenario(Session.newEmpty)(s).map { res ⇒
          res.isSuccess should be(true)
          uglyCounter.get() should be(effectNumber)
          res.logs.size should be(effectNumber + 2)
        }
      }

      "run all valid main effects and finally effects" in {
        val uglyCounter = new AtomicInteger(0)
        val effectNumber = 5
        val effect = EffectStep.fromSync(
          "increment captured counter",
          s ⇒ {
            uglyCounter.incrementAndGet()
            s
          }
        )

        val context = FeatureExecutionContext(finallySteps = List.fill(effectNumber)(effect))
        val s = Scenario("scenario with effects", context.finallySteps)
        engine.runScenario(Session.newEmpty, context)(s).map { res ⇒
          res.isSuccess should be(true)
          uglyCounter.get() should be(effectNumber * 2)
          res.logs.size should be(effectNumber * 2 + 3)
        }
      }
    }
  }
}
