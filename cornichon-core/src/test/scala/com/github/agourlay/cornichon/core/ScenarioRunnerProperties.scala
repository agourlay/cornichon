package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.wrapped._
import com.github.agourlay.cornichon.testHelpers.CommonTesting

import org.scalacheck.{Gen, Properties, Test}
import org.scalacheck.Prop.forAll

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._

class ScenarioRunnerProperties extends Properties("ScenarioRunner") with CommonTesting {

  // avoid lists too long (default: 100)
  override def overrideParameters(p: Test.Parameters): Test.Parameters = super.overrideParameters(p.withMaxSize(10))

  property("a scenario containing only valid steps should not fail") = forAll(validStepsGen) { validSteps =>
    val s = Scenario("scenario with valid steps", validSteps)
    val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    r.isSuccess
  }

  property("a scenario containing at least one invalid step should fail") = forAll(validStepsGen, invalidStepGen) { (validSteps, invalidStep) =>
    val s = Scenario("scenario with valid steps", validSteps :+ invalidStep)
    val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    !r.isSuccess
  }

  property("a scenario stops at the first failed step") = forAll(validStepsGen, invalidStepGen) { (validSteps, invalidStep) =>
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

  property("a scenario containing at least one invalid step should fail and always execute its finally clause") = forAll(validStepsGen, invalidStepGen) {
    (validSteps, invalidStep) =>
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

  property("a scenario fails if its finally clause contains invalid steps") = forAll(validStepsGen, invalidStepGen) { (validSteps, invalidStep) =>
    val context = FeatureContext.empty.copy(finallySteps = invalidStep :: Nil)
    val s = Scenario("scenario with valid steps", validSteps)
    val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty, context)(s))
    !r.isSuccess
  }

  property("a scenario with no steps succeeds") = {
    val s = Scenario("empty scenario", Nil)
    val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    r.isSuccess
  }

  property("runScenario does not run `main steps` if `beforeSteps` fail") = forAll(invalidStepGen) { invalidStep =>
    val signal = new AtomicBoolean(false)
    val signalingEffect = EffectStep.fromSync(
      "effect toggle signal",
      sc => {
        signal.set(true)
        sc.session
      }
    )
    val context = FeatureContext.empty.copy(beforeSteps = invalidStep :: Nil)
    val s = Scenario("scenario with failing before", signalingEffect :: Nil)
    val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty, context)(s))
    !r.isSuccess && !signal.get()
  }

  property("runScenario runs `main steps` if there is no failure in `beforeSteps`") = forAll(validStepsGen) { validSteps =>
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

  // === Session propagation through wrapper steps ===

  private val sessionWriteStep = EffectStep.fromSyncE("write to session", _.session.addValue("propagated-key", "propagated-value"))

  private def checkSessionStep(key: String, expectedValue: String) =
    EffectStep.fromSyncE(
      "check session",
      sc =>
        sc.session.get(key).flatMap { v =>
          if (v == expectedValue) Right(sc.session)
          else Left(CornichonError.fromString(s"expected '$expectedValue' but got '$v'"))
        }
    )

  private val wrapperGen: Gen[List[Step] => Step] = Gen.oneOf(
    (steps: List[Step]) => RepeatStep(steps, 1, None),
    (steps: List[Step]) => RepeatWithStep(steps, List("a"), "elem"),
    (steps: List[Step]) => RetryMaxStep(steps, 1),
    (steps: List[Step]) => WithinStep(steps, 5.seconds),
    (steps: List[Step]) => AttachStep(_ => steps),
    (steps: List[Step]) => LogDurationStep(steps, "timing")
  )

  property("session changes inside any wrapper step are visible after the wrapper") = forAll(wrapperGen) { wrapper =>
    val wrappedStep = wrapper(sessionWriteStep :: Nil)
    val checkStep = checkSessionStep("propagated-key", "propagated-value")
    val s = Scenario("session propagation", wrappedStep :: checkStep :: Nil)
    val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    r.isSuccess
  }

  // === Cleanup step propagation through wrapper steps ===

  property("cleanup steps inside any wrapper step are executed after scenario") = forAll(wrapperGen) { wrapper =>
    val cleanupRan = new AtomicBoolean(false)
    val resourceStep = ScenarioResourceStep(
      title = "test resource",
      acquire = EffectStep.fromSyncE("acquire", _.session.addValue("resource", "acquired")),
      release = EffectStep.fromSync("release", sc => { cleanupRan.set(true); sc.session })
    )
    val wrappedStep = wrapper(resourceStep :: Nil)
    val s = Scenario("cleanup propagation", wrappedStep :: Nil)
    val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    r.isSuccess && cleanupRan.get()
  }

  // === Session propagation through before/finally steps ===

  property("session from before steps is available in main steps") = forAll(validStepsGen) { validSteps =>
    val beforeStep = EffectStep.fromSyncE("before setup", _.session.addValue("from-before", "yes"))
    val checkStep = checkSessionStep("from-before", "yes")
    val context = FeatureContext.empty.copy(beforeSteps = beforeStep :: validSteps)
    val s = Scenario("before session", checkStep :: Nil)
    val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty, context)(s))
    r.isSuccess
  }

  property("session from main steps is available in finally steps") = forAll(validStepsGen) { validSteps =>
    val signal = new AtomicBoolean(false)
    val mainStep = EffectStep.fromSyncE("main setup", _.session.addValue("from-main", "yes"))
    val finallyStep = EffectStep.fromSync(
      "finally check",
      sc => {
        if (sc.session.getOpt("from-main").contains("yes")) signal.set(true)
        sc.session
      }
    )
    val context = FeatureContext.empty.copy(finallySteps = finallyStep :: Nil)
    val s = Scenario("finally session", validSteps :+ mainStep)
    val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty, context)(s))
    r.isSuccess && signal.get()
  }

}
