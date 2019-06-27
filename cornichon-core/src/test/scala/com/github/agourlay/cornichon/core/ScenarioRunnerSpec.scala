package com.github.agourlay.cornichon.core

import java.util.concurrent.atomic.AtomicInteger

import com.github.agourlay.cornichon.dsl.ProvidedInstances._
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import com.github.agourlay.cornichon.util.TaskSpec
import org.scalatest.{ AsyncWordSpec, Matchers }

class ScenarioRunnerSpec extends AsyncWordSpec with Matchers with TaskSpec {

  "ScenarioRunner" when {
    "runScenario" must {
      "executes all steps of a scenario" in {
        val steps = AssertStep("first step", _ ⇒ GenericEqualityAssertion(2 + 1, 3)) :: Nil
        val s = Scenario("test", steps)
        ScenarioRunner.runScenario(Session.newEmpty)(s).map { r ⇒
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
        ScenarioRunner.runScenario(Session.newEmpty)(s).map { res ⇒
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
        val fc = FeatureContext.empty.copy(finallySteps = finalAssertion :: Nil, withSeed = Some(1))
        ScenarioRunner.runScenario(Session.newEmpty, fc)(s).map {
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
                |
                |seed for the run was '1'
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
          sc ⇒ {
            uglyCounter.incrementAndGet()
            sc.session
          }
        )

        val s = Scenario("scenario with effects", List.fill(effectNumber)(effect))
        ScenarioRunner.runScenario(Session.newEmpty)(s).map { res ⇒
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
          sc ⇒ {
            uglyCounter.incrementAndGet()
            sc.session
          }
        )

        val context = FeatureContext.empty.copy(finallySteps = List.fill(effectNumber)(effect))
        val s = Scenario("scenario with effects", context.finallySteps)
        ScenarioRunner.runScenario(Session.newEmpty, context)(s).map { res ⇒
          res.isSuccess should be(true)
          uglyCounter.get() should be(effectNumber * 2)
          res.logs.size should be(effectNumber * 2 + 3)
        }
      }

      "have a deterministic run via a fixed seed" in {
        val fixedTestSeed = 12345L
        val fc = FeatureContext.empty.copy(withSeed = Some(fixedTestSeed))

        val assertSeed = AssertStep("assert seed", sc ⇒ {
          GenericEqualityAssertion(sc.randomContext.initialSeed, fixedTestSeed)
        })

        val rdStep = EffectStep.fromSyncE("pick random int", sc ⇒ {
          val rdInt = sc.randomContext.seededRandom.nextInt()
          sc.session.addValue("random-int", rdInt.toString)
        })

        val rdAssert = AssertStep("assert rd", sc ⇒ {
          val rdValue = sc.session.getUnsafe("random-int")
          GenericEqualityAssertion(rdValue, "1553932502") // value generated with the fixedTestSeed
        })

        val steps = assertSeed :: rdStep :: rdAssert :: Nil
        val s = Scenario("deterministic test", steps)
        ScenarioRunner.runScenario(Session.newEmpty, fc)(s).map { r ⇒
          r.isSuccess should be(true)
          r.logs.size should be(5)
        }
      }
    }
  }
}
