package com.github.agourlay.cornichon.testHelpers

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.regular.DebugStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion }
import org.scalacheck.Gen

import scala.util.control.NoStackTrace

trait CommonTesting extends TaskSpec {

  def integerGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
    name = "integer",
    gen = () => rc.nextInt(10000))

  def brokenIntGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
    name = "integer",
    gen = () => throw new RuntimeException(s"boom gen with initial seed ${rc.initialSeed}"))

  def stringGen(rc: RandomContext): ValueGenerator[String] = ValueGenerator(
    name = "integer",
    gen = () => rc.nextString(10))

  def printScenarioLogs(sr: ScenarioReport): Unit =
    LogInstruction.printLogs(sr.logs)

  val identityEffectStep: Step = EffectStep.fromSync("identity effect step", _.session)
  val addValueToSessionEffectStep: Step = EffectStep.fromSyncE("add value to session effect step", _.session.addValue("my-key", "my-value"))
  val alwaysValidAssertStep: Step = AssertStep("valid", _ => Assertion.alwaysValid)
  val validDebugStep: Step = DebugStep("valid debug", _ => Right("debug!"))

  val validStepGen: Gen[Step] = Gen.oneOf(identityEffectStep, addValueToSessionEffectStep, alwaysValidAssertStep, validDebugStep)
  val validStepsGen: Gen[List[Step]] = Gen.nonEmptyListOf(validStepGen)

  def throwExceptionWithStackTrace() = throw new RuntimeException("boom!") with NoStackTrace

  val brokenEffectStep: Step = EffectStep.fromSyncE("always boom", _ => Left(CornichonError.fromString("boom!")))
  val exceptionEffectStep: Step = EffectStep.fromSync("throw exception effect step", _ => throwExceptionWithStackTrace())
  val neverValidAssertStep: Step = AssertStep("never valid assert step", _ => Assertion.failWith("never valid!"))
  val failedDebugStep: Step = DebugStep("bad debug", _ => throwExceptionWithStackTrace())

  val invalidStepGen: Gen[Step] = Gen.oneOf(brokenEffectStep, exceptionEffectStep, neverValidAssertStep, failedDebugStep)
  val invalidStepsGen: Gen[List[Step]] = Gen.nonEmptyListOf(invalidStepGen)

  // TODO generate wrapper steps with invalid and valid nested steps
}