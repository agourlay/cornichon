package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.dsl.SessionSteps.SessionStepBuilder
import com.github.agourlay.cornichon.steps.regular.assertStep.{ GenericEqualityAssertion, LessThanAssertion }
import com.github.agourlay.cornichon.testHelpers.CommonTesting
import org.scalacheck.Prop.forAll
import org.scalacheck.{ Gen, Properties }

class SessionStepsProperties extends Properties("SessionSteps") with CommonTesting {

  private val testKey = "test-key"
  private val sessionStepBuilder = SessionStepBuilder(SessionKey(testKey))

  property("sessionStep is value") =
    forAll { input: String =>
      val session = Session.newEmpty.addValuesUnsafe(testKey -> input)
      val step = sessionStepBuilder.is(input)
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitIO(ScenarioRunner.runScenario(session)(s))
      t.isSuccess
    }

  property("sessionStep hasDifferentCurrentAndPreviousValues") =
    forAll { input: String =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> input)
        .addValuesUnsafe(testKey -> (input + "something"))
      val step = sessionStepBuilder.hasDifferentCurrentAndPreviousValues
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitIO(ScenarioRunner.runScenario(session)(s))
      t.isSuccess
    }

  property("sessionStep hasDifferentCurrentAndPreviousValues - failure") =
    forAll { input: String =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> input)
        .addValuesUnsafe(testKey -> input)
      val step = sessionStepBuilder.hasDifferentCurrentAndPreviousValues
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitIO(ScenarioRunner.runScenario(session)(s))
      !t.isSuccess
    }

  property("sessionStep hasEqualCurrentAndPreviousValues") =
    forAll { input: String =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> input)
        .addValuesUnsafe(testKey -> input)
      val step = sessionStepBuilder.hasEqualCurrentAndPreviousValues
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitIO(ScenarioRunner.runScenario(session)(s))
      t.isSuccess
    }

  property("sessionStep hasEqualCurrentAndPreviousValues - failure") =
    forAll { input: String =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> input)
        .addValuesUnsafe(testKey -> (input + "something"))
      val step = sessionStepBuilder.hasEqualCurrentAndPreviousValues
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitIO(ScenarioRunner.runScenario(session)(s))
      !t.isSuccess
    }

  property("sessionStep compareWithPreviousValue - equality") =
    forAll { input: String =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> input)
        .addValuesUnsafe(testKey -> input)
      val step = sessionStepBuilder.compareWithPreviousValue { case (prev, current) => GenericEqualityAssertion(prev, current) }
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitIO(ScenarioRunner.runScenario(session)(s))
      t.isSuccess
    }

  property("sessionStep compareWithPreviousValue - LessThanAssertion") =
    forAll { input: String =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> input)
        .addValuesUnsafe(testKey -> (input + "something"))
      val step = sessionStepBuilder.compareWithPreviousValue { case (prev, current) => LessThanAssertion(prev.length, current.length) }
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitIO(ScenarioRunner.runScenario(session)(s))
      t.isSuccess
    }

  property("sessionStep compareWithPreviousValue - LessThanAssertion - failure") =
    forAll { input: String =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> input)
        .addValuesUnsafe(testKey -> input)
      val step = sessionStepBuilder.compareWithPreviousValue { case (prev, current) => LessThanAssertion(prev.length, current.length) }
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitIO(ScenarioRunner.runScenario(session)(s))
      !t.isSuccess
    }

  property("sessionStep containsString") =
    forAll { input: String =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> ("prefix" + input + "suffix"))
      val stepP = sessionStepBuilder.containsString("prefix")
      val stepI = if (input.nonEmpty) sessionStepBuilder.containsString(input) else alwaysValidAssertStep
      val stepS = sessionStepBuilder.containsString("suffix")
      val s = Scenario("scenario with SessionSteps", stepP :: stepI :: stepS :: Nil)
      val t = awaitIO(ScenarioRunner.runScenario(session)(s))
      t.isSuccess
    }

  property("sessionStep containsString - failure") =
    forAll { input: String =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> input)
      val step = sessionStepBuilder.containsString(input + "42")
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitIO(ScenarioRunner.runScenario(session)(s))
      !t.isSuccess
    }

  property("sessionStep matchesRegex") =
    forAll(Gen.alphaStr) { input =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> ("prefix" + input + "suffix"))
      val stepP = sessionStepBuilder.matchesRegex(".*pref".r)
      val stepI = if (input.nonEmpty) sessionStepBuilder.matchesRegex(s".*$input".r) else alwaysValidAssertStep
      val stepS = sessionStepBuilder.matchesRegex(".*suff".r)
      val s = Scenario("scenario with SessionSteps", stepP :: stepI :: stepS :: Nil)
      val t = awaitIO(ScenarioRunner.runScenario(session)(s))
      t.isSuccess
    }

  property("sessionStep matchesRegex - failure") =
    forAll(Gen.alphaStr) { input =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> input)
      val unknownInput = input + "42"
      val step = sessionStepBuilder.matchesRegex(s".*${unknownInput}".r)
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitIO(ScenarioRunner.runScenario(session)(s))
      if (t.isSuccess) printScenarioLogs(t)
      !t.isSuccess
    }

}
