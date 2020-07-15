package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.dsl.SessionSteps.SessionStepBuilder
import com.github.agourlay.cornichon.steps.regular.assertStep.{ GenericEqualityAssertion, LessThanAssertion }
import com.github.agourlay.cornichon.testHelpers.TaskSpec
import cats.instances.string._
import cats.instances.int._
import org.scalacheck.Prop.forAll
import org.scalacheck.Properties
import org.typelevel.claimant.Claim

class SessionStepsProperties extends Properties("SessionSteps") with TaskSpec {

  private val testKey = "test-key"
  private val sessionStepBuilder = SessionStepBuilder(testKey)

  property("sessionStep is value") =
    forAll { input: String =>
      val session = Session.newEmpty.addValuesUnsafe(testKey -> input)
      val step = sessionStepBuilder.is(input)
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitTask(ScenarioRunner.runScenario(session)(s))
      Claim(t.isSuccess)
    }

  property("sessionStep hasDifferentCurrentAndPreviousValues") =
    forAll { input: String =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> input)
        .addValuesUnsafe(testKey -> (input + "something"))
      val step = sessionStepBuilder.hasDifferentCurrentAndPreviousValues
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitTask(ScenarioRunner.runScenario(session)(s))
      Claim(t.isSuccess)
    }

  property("sessionStep hasDifferentCurrentAndPreviousValues - failure") =
    forAll { input: String =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> input)
        .addValuesUnsafe(testKey -> input)
      val step = sessionStepBuilder.hasDifferentCurrentAndPreviousValues
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitTask(ScenarioRunner.runScenario(session)(s))
      Claim(!t.isSuccess)
    }

  property("sessionStep hasEqualCurrentAndPreviousValues") =
    forAll { input: String =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> input)
        .addValuesUnsafe(testKey -> input)
      val step = sessionStepBuilder.hasEqualCurrentAndPreviousValues
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitTask(ScenarioRunner.runScenario(session)(s))
      Claim(t.isSuccess)
    }

  property("sessionStep hasEqualCurrentAndPreviousValues - failure") =
    forAll { input: String =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> input)
        .addValuesUnsafe(testKey -> (input + "something"))
      val step = sessionStepBuilder.hasEqualCurrentAndPreviousValues
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitTask(ScenarioRunner.runScenario(session)(s))
      Claim(!t.isSuccess)
    }

  property("sessionStep compareWithPreviousValue - equality") =
    forAll { input: String =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> input)
        .addValuesUnsafe(testKey -> input)
      val step = sessionStepBuilder.compareWithPreviousValue { case (prev, current) => GenericEqualityAssertion(prev, current) }
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitTask(ScenarioRunner.runScenario(session)(s))
      Claim(t.isSuccess)
    }

  property("sessionStep compareWithPreviousValue - LessThanAssertion") =
    forAll { input: String =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> input)
        .addValuesUnsafe(testKey -> (input + "something"))
      val step = sessionStepBuilder.compareWithPreviousValue { case (prev, current) => LessThanAssertion(prev.length, current.length) }
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitTask(ScenarioRunner.runScenario(session)(s))
      Claim(t.isSuccess)
    }

  property("sessionStep compareWithPreviousValue - LessThanAssertion - failure") =
    forAll { input: String =>
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> input)
        .addValuesUnsafe(testKey -> input)
      val step = sessionStepBuilder.compareWithPreviousValue { case (prev, current) => LessThanAssertion(prev.length, current.length) }
      val s = Scenario("scenario with SessionSteps", step :: Nil)
      val t = awaitTask(ScenarioRunner.runScenario(session)(s))
      Claim(!t.isSuccess)
    }

}
