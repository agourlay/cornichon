package com.github.agourlay.cornichon.core

import java.util.concurrent.atomic.AtomicInteger
import com.github.agourlay.cornichon.resolver.PlaceholderResolver
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import munit.FunSuite

class ScenarioRunnerSpec extends FunSuite with CommonTestSuite {

  test("runScenario executes all steps of a scenario in case of success") {
    val beforeSteps = AssertStep("before assertion", _ => GenericEqualityAssertion(2 + 1, 3)) :: Nil
    val steps = AssertStep("main assertion", _ => GenericEqualityAssertion(2 + 1, 3)) :: Nil
    val finallySteps = AssertStep("finally assertion", _ => GenericEqualityAssertion(2 + 1, 3)) :: Nil
    val fc = FeatureContext.empty.copy(
      beforeSteps = beforeSteps,
      finallySteps = finallySteps
    )
    val s = Scenario("casual stuff", steps)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty, fc)(s))
    assert(res.isSuccess)
    matchLogsWithoutDuration(res.logs) {
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

  test("runScenario does not run `main steps` if there is a failure in `beforeSteps`") {
    val beforeSteps = AssertStep("before assertion", _ => GenericEqualityAssertion(2 + 1, 4)) :: Nil
    val steps = AssertStep("main assertion", _ => GenericEqualityAssertion(2 + 1, 3)) :: Nil
    val finallySteps = AssertStep("finally assertion", _ => GenericEqualityAssertion(2 + 1, 3)) :: Nil
    val fc = FeatureContext.empty.copy(
      beforeSteps = beforeSteps,
      finallySteps = finallySteps
    )
    val s = Scenario("casual stuff", steps)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty, fc)(s))
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

  test("runScenario stops at first failed step") {
    val step1 = AssertStep("first step", _ => GenericEqualityAssertion(2, 2))
    val step2 = AssertStep("second step", _ => GenericEqualityAssertion(4, 5))
    val step3 = AssertStep("third step", _ => GenericEqualityAssertion(1, 1))
    val steps = step1 :: step2 :: step3 :: Nil
    val s = Scenario("early stop", steps)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
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

  test("runScenario accumulates errors if 'main' and 'finally' fail") {
    val mainStep = AssertStep("main assertion", _ => GenericEqualityAssertion(true, false))
    val finalAssertion = AssertStep("finally assertion", _ => GenericEqualityAssertion(true, false))
    val s = Scenario("accumulate", mainStep :: Nil)
    val fc = FeatureContext.empty.copy(finallySteps = finalAssertion :: Nil, withSeed = Some(1))
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty, fc)(s))
    scenarioFailsWithMessage(res) {
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

    matchLogsWithoutDuration(res.logs) {
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

  test("runScenario runs all valid effects") {
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
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    assert(uglyCounter.get() == effectNumber)
    assert(res.logs.size == effectNumber + 2)
  }

  test("runScenario runs all valid main effects and finally effects") {
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
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty, context)(s))
    assert(res.isSuccess)
    assert(uglyCounter.get() == effectNumber * 2)
    assert(res.logs.size == effectNumber * 2 + 3)
  }

  test("runScenario has deterministic runs via a fixed seed") {
    val fixedTestSeed = 12345L
    val fc = FeatureContext.empty.copy(withSeed = Some(fixedTestSeed))

    val assertSeed = AssertStep("assert seed", sc => {
      GenericEqualityAssertion(sc.randomContext.initialSeed, fixedTestSeed)
    })

    val rdStep = EffectStep.fromSyncE("pick random int", sc => {
      val rdInt = sc.randomContext.nextInt()
      sc.session.addValue("random-int", rdInt.toString)
    })

    val rdAssert = AssertStep("assert rd", sc => {
      val rdValue = sc.session.getUnsafe("random-int")
      GenericEqualityAssertion(rdValue, "1553932502") // value generated with the fixedTestSeed
    })

    val steps = assertSeed :: rdStep :: rdAssert :: Nil
    val s = Scenario("deterministic test", steps)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty, fc)(s))
    assert(res.isSuccess)
    assert(res.logs.size == 5)
  }

  test("runScenario resolves session placeholders in steps title - fails if unknown placeholder") {
    val steps = AssertStep("an assertion <unknown-placeholder>", _ => Assertion.alwaysValid) :: Nil
    val s = Scenario("casual stuff", steps)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    scenarioFailsWithMessage(res) {
      """Scenario 'casual stuff' failed:
          |
          |at step:
          |an assertion <unknown-placeholder>
          |
          |with error(s):
          |key 'unknown-placeholder' can not be found in session
          |empty
          |
          |seed for the run was '1'
          |""".stripMargin
    }
  }

  test("runScenario resolves session placeholders in steps title") {
    val session = Session.newEmpty.addValuesUnsafe("known-placeholder" -> "found!")
    val steps = AssertStep("an assertion <known-placeholder>", _ => Assertion.alwaysValid) :: Nil
    val s = Scenario("casual stuff", steps)
    val res = awaitIO(ScenarioRunner.runScenario(session)(s))
    assert(res.isSuccess)
    matchLogsWithoutDuration(res.logs) {
      """
          |   Scenario : casual stuff
          |      main steps
          |      an assertion found!""".stripMargin
    }
  }

  test("runScenario does not resolve non-deterministic built-in placeholders in steps title") {
    PlaceholderResolver.builtInPlaceholderGenerators.foreach { ph =>
      val steps = AssertStep(s"an assertion <${ph.key}>", _ => Assertion.alwaysValid) :: Nil
      val s = Scenario("casual stuff", steps)
      val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(res.isSuccess)
      matchLogsWithoutDuration(res.logs) {
        s"""
            |   Scenario : casual stuff
            |      main steps
            |      an assertion <${ph.key}>""".stripMargin
      }
    }
  }
}
