package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.testHelpers.CommonTesting

import org.scalacheck.Prop.forAll
import org.scalacheck.{ Properties, Test }

import java.util.concurrent.atomic.AtomicBoolean

class ScenarioRunnerProperties extends Properties("ScenarioRunner") with CommonTesting {

  // avoid lists too long (default: 100)
  override def overrideParameters(p: Test.Parameters): Test.Parameters = super.overrideParameters(p.withMaxSize(10))

  property("a scenario containing only valid steps should not fail") =
    forAll(validStepsGen) { validSteps =>
      val s = Scenario("scenario with valid steps", validSteps)
      val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
      r.isSuccess
    }

  property("a scenario containing at least one invalid step should fail") =
    forAll(validStepsGen, invalidStepGen) { (validSteps, invalidStep) =>
      val s = Scenario("scenario with valid steps", validSteps :+ invalidStep)
      val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
      !r.isSuccess
    }

  property("a scenario stops at the first failed step") =
    forAll(validStepsGen, invalidStepGen) { (validSteps, invalidStep) =>
      val signal = new AtomicBoolean(false)
      val signalingEffect = EffectStep.fromSync(
        "effect toggle signal",
        sc => {
          signal.set(true)
          sc.session
        }
      )
      val s = Scenario("scenario with valid steps", validSteps :+ invalidStep :+ signalingEffect)
      val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
      !r.isSuccess && !signal.get()
    }

  property("a scenario containing at least one invalid step should fail and always execute its finally clause") =
    forAll(validStepsGen, invalidStepGen) { (validSteps, invalidStep) =>
      val signal = new AtomicBoolean(false)
      val signallingEffect = EffectStep.fromSync(
        "effect toggle signal",
        sc => {
          signal.set(true)
          sc.session
        }
      )
      val context = FeatureContext.empty.copy(finallySteps = signallingEffect :: Nil)
      val s = Scenario("scenario with valid steps", validSteps :+ invalidStep)
      val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty, context)(s))
      !r.isSuccess && signal.get()
    }

  property("a scenario fails if its finally clause contains invalid steps") =
    forAll(validStepsGen, invalidStepGen) { (validSteps, invalidStep) =>
      val context = FeatureContext.empty.copy(finallySteps = invalidStep :: Nil)
      val s = Scenario("scenario with valid steps", validSteps)
      val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty, context)(s))
      !r.isSuccess
    }

  property("runScenario runs `main steps` if there is no failure in `beforeSteps`") =
    forAll(validStepsGen) { validSteps =>
      val signal = new AtomicBoolean(false)
      val signalingEffect = EffectStep.fromSync(
        "effect toggle signal",
        sc => {
          signal.set(true)
          sc.session
        }
      )
      val context = FeatureContext.empty.copy(beforeSteps = validSteps)
      val s = Scenario("scenario with valid steps", signalingEffect :: Nil)
      val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty, context)(s))
      r.isSuccess && signal.get()
    }

}
