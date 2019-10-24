package com.github.agourlay.cornichon.check

import com.github.agourlay.cornichon.core.{ CornichonError, RandomContext, Step }
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion }
import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.duration.Duration

trait CheckStepUtil {
  // TODO use StepUtilSpec through some kind of common-testing
  implicit val scheduler: Scheduler = Scheduler.Implicits.global
  def awaitTask[A](t: Task[A]): A = t.runSyncUnsafe(Duration.Inf)

  def integerGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
    name = "integer",
    gen = () ⇒ rc.seededRandom.nextInt(10000))

  def brokenIntGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
    name = "integer",
    gen = () ⇒ throw new RuntimeException("boom gen!"))

  def stringGen(rc: RandomContext): ValueGenerator[String] = ValueGenerator(
    name = "integer",
    gen = () ⇒ rc.seededRandom.nextString(10))

  val alwaysValidAssertStep = AssertStep("valid", _ ⇒ Assertion.alwaysValid)

  val brokenEffect: Step = EffectStep.fromSyncE("always boom", _ ⇒ Left(CornichonError.fromString("boom!")))

  val neverValidAssertStep = AssertStep("never valid assert step", _ ⇒ Assertion.failWith("never valid!"))

  val identityStep: Step = EffectStep.fromSync("identity effect step", _.session)
}
