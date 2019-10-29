package com.github.agourlay.cornichon.core

import java.util.concurrent.atomic.AtomicInteger

import com.github.agourlay.cornichon.dsl.ProvidedInstances._
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import com.github.agourlay.cornichon.util.{ ScenarioMatchers, TaskSpec }
import org.scalatest.{ AsyncWordSpec, Matchers }

class ScenarioRunnerSpec extends AsyncWordSpec with Matchers with TaskSpec with ScenarioMatchers {

  "ScenarioRunner" when {
    "runScenario" must {
      "executes all steps of a scenario in case of success" in {
        val beforeSteps = AssertStep("before assertion", _ => GenericEqualityAssertion(2 + 1, 3)) :: Nil
        val steps = AssertStep("main assertion", _ => GenericEqualityAssertion(2 + 1, 3)) :: Nil
        val finallySteps = AssertStep("finally assertion", _ => GenericEqualityAssertion(2 + 1, 3)) :: Nil
        val fc = FeatureContext.empty.copy(
          beforeSteps = beforeSteps,
          finallySteps = finallySteps
        )
        val s = Scenario("casual stuff", steps)
        ScenarioRunner.runScenario(Session.newEmpty, fc)(s).map { r =>
          r.isSuccess should be(true)
          matchLogsWithoutDuration(r.logs) {
            """
              |   Scenario : casual stuff
              |      before steps
              |      before assertion
              |      main steps
              |      main assertion
              |      finally steps
              |      finally assertion""".stripMargin
          }
        }
      }

      "do not run `main steps` if there is a failure in `beforeSteps`" in {
        val beforeSteps = AssertStep("before assertion", _ => GenericEqualityAssertion(2 + 1, 4)) :: Nil
        val steps = AssertStep("main assertion", _ => GenericEqualityAssertion(2 + 1, 3)) :: Nil
        val finallySteps = AssertStep("finally assertion", _ => GenericEqualityAssertion(2 + 1, 3)) :: Nil
        val fc = FeatureContext.empty.copy(
          beforeSteps = beforeSteps,
          finallySteps = finallySteps
        )
        val s = Scenario("casual stuff", steps)
        ScenarioRunner.runScenario(Session.newEmpty, fc)(s).map { res =>
          scenarioFailsWithMessage(res) {
            """Scenario 'casual stuff' failed:
              |
              |at step:
              |before assertion
              |
              |with error(s):
              |expected result was:
              |'3'
              |but actual result is:
              |'4'
              |
              |seed for the run was '1'
              |""".stripMargin
          }

          matchLogsWithoutDuration(res.logs) {
            """
              |   Scenario : casual stuff
              |      before steps
              |      before assertion
              |      *** FAILED ***
              |      expected result was:
              |      '3'
              |      but actual result is:
              |      '4'
              |      finally steps
              |      finally assertion""".stripMargin
          }
        }
      }

      "stops at first failed step" in {
        val step1 = AssertStep("first step", _ => GenericEqualityAssertion(2, 2))
        val step2 = AssertStep("second step", _ => GenericEqualityAssertion(4, 5))
        val step3 = AssertStep("third step", _ => GenericEqualityAssertion(1, 1))
        val steps = step1 :: step2 :: step3 :: Nil
        val s = Scenario("early stop", steps)
        ScenarioRunner.runScenario(Session.newEmpty)(s).map { res =>
          scenarioFailsWithMessage(res) {
            """Scenario 'early stop' failed:
              |
              |at step:
              |second step
              |
              |with error(s):
              |expected result was:
              |'4'
              |but actual result is:
              |'5'
              |
              |seed for the run was '1'
              |""".stripMargin
          }

          matchLogsWithoutDuration(res.logs) {
            """
              |   Scenario : early stop
              |      main steps
              |      first step
              |      second step
              |      *** FAILED ***
              |      expected result was:
              |      '4'
              |      but actual result is:
              |      '5'""".stripMargin
          }
        }
      }

      "accumulates errors if 'main' and 'finally' fail" in {
        val mainStep = AssertStep("main assertion", _ => GenericEqualityAssertion(true, false))
        val finalAssertion = AssertStep("finally assertion", _ => GenericEqualityAssertion(true, false))
        val s = Scenario("accumulate", mainStep :: Nil)
        val fc = FeatureContext.empty.copy(finallySteps = finalAssertion :: Nil, withSeed = Some(1))
        ScenarioRunner.runScenario(Session.newEmpty, fc)(s).map { r =>
          scenarioFailsWithMessage(r) {
            """Scenario 'accumulate' failed:
              |
              |at step:
              |main assertion
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
              |finally assertion
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

          matchLogsWithoutDuration(r.logs) {
            """
              |   Scenario : accumulate
              |      main steps
              |      main assertion
              |      *** FAILED ***
              |      expected result was:
              |      'true'
              |      but actual result is:
              |      'false'
              |      finally steps
              |      finally assertion
              |      *** FAILED ***
              |      expected result was:
              |      'true'
              |      but actual result is:
              |      'false'""".stripMargin
          }
        }
      }

      "run all valid effects" in {
        val uglyCounter = new AtomicInteger(0)
        val effectNumber = 5
        val effect = EffectStep.fromSync(
          "increment captured counter",
          sc => {
            uglyCounter.incrementAndGet()
            sc.session
          }
        )

        val s = Scenario("scenario with effects", List.fill(effectNumber)(effect))
        ScenarioRunner.runScenario(Session.newEmpty)(s).map { res =>
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
          sc => {
            uglyCounter.incrementAndGet()
            sc.session
          }
        )

        val context = FeatureContext.empty.copy(finallySteps = List.fill(effectNumber)(effect))
        val s = Scenario("scenario with effects", context.finallySteps)
        ScenarioRunner.runScenario(Session.newEmpty, context)(s).map { res =>
          res.isSuccess should be(true)
          uglyCounter.get() should be(effectNumber * 2)
          res.logs.size should be(effectNumber * 2 + 3)
        }
      }

      "have a deterministic run via a fixed seed" in {
        val fixedTestSeed = 12345L
        val fc = FeatureContext.empty.copy(withSeed = Some(fixedTestSeed))

        val assertSeed = AssertStep("assert seed", sc => {
          GenericEqualityAssertion(sc.randomContext.initialSeed, fixedTestSeed)
        })

        val rdStep = EffectStep.fromSyncE("pick random int", sc => {
          val rdInt = sc.randomContext.seededRandom.nextInt()
          sc.session.addValue("random-int", rdInt.toString)
        })

        val rdAssert = AssertStep("assert rd", sc => {
          val rdValue = sc.session.getUnsafe("random-int")
          GenericEqualityAssertion(rdValue, "1553932502") // value generated with the fixedTestSeed
        })

        val steps = assertSeed :: rdStep :: rdAssert :: Nil
        val s = Scenario("deterministic test", steps)
        ScenarioRunner.runScenario(Session.newEmpty, fc)(s).map { r =>
          r.isSuccess should be(true)
          r.logs.size should be(5)
        }
      }
    }
  }
}
