package com.github.agourlay.cornichon.core

import cats.data.Xor
import cats.data.Xor.{ left, right }
import com.github.agourlay.cornichon.core.ScenarioReport._
import com.github.agourlay.cornichon.steps.regular.{ AssertStep, EffectStep }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

class Engine(executionContext: ExecutionContext) {

  private implicit val ec = executionContext

  def runScenario(session: Session, finallySteps: Seq[Step] = Seq.empty)(scenario: Scenario): ScenarioReport = {
    val initMargin = 1
    val titleLog = Vector(DefaultLogInstruction(s"Scenario : ${scenario.name}", initMargin))
    val mainExecution = fromStepsReport(scenario, runSteps(scenario.steps, session, titleLog, initMargin + 1))
    if (finallySteps.isEmpty)
      mainExecution
    else {
      val finallyExecution = fromStepsReport(scenario, runSteps(finallySteps.toVector, mainExecution.session, Vector.empty, initMargin + 1))
      mainExecution.merge(finallyExecution)
    }
  }

  def runSteps(steps: Vector[Step], session: Session, logs: Vector[LogInstruction], depth: Int): StepsReport =
    steps.headOption.fold[StepsReport](SuccessRunSteps(session, logs)) { step ⇒
      step.run(this, session, logs, depth) match {
        case SuccessRunSteps(newSession, updatedLogs) ⇒
          val nextSteps = steps.tail.drop(1)
          runSteps(nextSteps, newSession, updatedLogs, depth)
        case f: FailedRunSteps ⇒
          f
      }
    }

  def buildFailedRunSteps(currentStep: Step, remainingSteps: Vector[Step], error: Throwable, logs: Vector[LogInstruction], depth: Int, session: Session): FailedRunSteps = {
    val e = toCornichonError(error)
    val failedStep = FailedStep(currentStep, e)
    val notExecutedStep = remainingSteps.collect {
      case AssertStep(t, _, _, true) ⇒ t
      case EffectStep(t, _, true)    ⇒ t
    }
    FailedRunSteps(failedStep, notExecutedStep, logs, session)
  }

  def XorToStepReport(currentStep: Step, session: Session, logs: Vector[LogInstruction], res: Xor[CornichonError, Session], title: String, depth: Int, show: Boolean, duration: Option[Duration] = None) =
    res match {
      case Xor.Left(e) ⇒
        val updatedLogs = logs ++ errorLogs(title, e, depth, ???)
        buildFailedRunSteps(currentStep, ???, e, updatedLogs, depth, session)

      case Xor.Right(newSession) ⇒
        val updatedLogs = if (show) logs :+ SuccessLogInstruction(title, depth, duration) else logs
        SuccessRunSteps(newSession, updatedLogs)
    }

  def toCornichonError(exception: Throwable): CornichonError = exception match {
    case ce: CornichonError ⇒ ce
    case _                  ⇒ StepExecutionError(exception)
  }

  private[cornichon] def runStepPredicate[A](negateStep: Boolean, newSession: Session, stepAssertion: StepAssertion[A]): Xor[CornichonError, Session] = {
    val succeedAsExpected = stepAssertion.isSuccess && !negateStep
    val failedAsExpected = !stepAssertion.isSuccess && negateStep

    if (succeedAsExpected || failedAsExpected) right(newSession)
    else
      stepAssertion match {
        case SimpleStepAssertion(expected, actual) ⇒
          left(StepAssertionError(expected, actual, negateStep))
        case DetailedStepAssertion(expected, actual, details) ⇒
          left(DetailedStepAssertionError(actual, details))
      }
  }

  def logNonExecutedStep(steps: Seq[Step], depth: Int): Seq[LogInstruction] =
    steps.collect {
      case a @ AssertStep(_, _, _, true) ⇒ a
      case e @ EffectStep(_, _, true)    ⇒ e
    }.map { step ⇒
      InfoLogInstruction(step.title, depth)
    }

  def errorLogs(title: String, e: Throwable, depth: Int, remainingSteps: Vector[Step]) = {
    val error = toCornichonError(e)
    val logStepErrorResult = Vector(FailureLogInstruction(s"$title *** FAILED ***", depth)) ++ error.msg.split('\n').map { m ⇒
      FailureLogInstruction(m, depth)
    }
    logStepErrorResult ++ logNonExecutedStep(remainingSteps, depth)
  }

  def withDuration[A](fct: ⇒ A): (A, Duration) = {
    val now = System.nanoTime
    val res = fct
    val executionTime = Duration.fromNanos(System.nanoTime - now)
    (res, executionTime)
  }
}