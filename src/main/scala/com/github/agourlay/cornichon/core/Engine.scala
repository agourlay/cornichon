package com.github.agourlay.cornichon.core

import cats.data.Xor
import cats.data.Xor.{ left, right }

import scala.Console._
import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.util._

class Engine {

  def runScenario(scenario: Scenario)(session: Session): ScenarioReport = {
    val initLogs = Seq(DefaultLogInstruction(s"Scenario : ${scenario.name}"))
    runSteps(scenario.steps, session, EventuallyConf.empty, None, initLogs) match {
      case s @ SuccessRunSteps(_)      ⇒ SuccessScenarioReport(scenario, s)
      case f @ FailedRunSteps(_, _, _) ⇒ FailedScenarioReport(scenario, f)
    }
  }

  @tailrec
  private def runSteps(steps: Seq[Step], session: Session, eventuallyConf: EventuallyConf, snapshot: Option[RollbackSnapshot], logs: Seq[LogInstruction]): StepsReport = {
    if (steps.isEmpty) SuccessRunSteps(logs)
    else
      steps.head match {
        case DebugStep(message) ⇒
          runSteps(steps.tail, session, eventuallyConf, snapshot, logs :+ ColoredLogInstruction(message(session), CYAN))

        case EventuallyStart(conf) ⇒
          val updatedLogs = logs :+ DefaultLogInstruction(s"   Eventually bloc with maxDuration = ${conf.maxTime} and interval = ${conf.interval}")
          runSteps(steps.tail, session, conf, Some(RollbackSnapshot(steps.tail, session)), updatedLogs)

        case EventuallyStop(conf) ⇒
          val updatedLogs = logs :+ DefaultLogInstruction(s"   Eventually bloc succeeded in ${conf.maxTime.toSeconds - eventuallyConf.maxTime.toSeconds} sec.")
          runSteps(steps.tail, session, EventuallyConf.empty, None, updatedLogs)

        case currentStep: ExecutableStep[_] ⇒
          val now = System.nanoTime
          val stepResult = runStepAction(currentStep)(session)
          val executionTime = Duration.fromNanos(System.nanoTime - now)
          stepResult match {
            case Xor.Left(e) ⇒
              val remainingTime = eventuallyConf.maxTime - executionTime
              if (remainingTime.gt(Duration.Zero)) {
                val updatedLogs = logs ++ logStepErrorResult(currentStep, e, CYAN)
                Thread.sleep(eventuallyConf.interval.toMillis)
                runSteps(snapshot.get.steps, snapshot.get.session, eventuallyConf.consume(executionTime + eventuallyConf.interval), snapshot, updatedLogs)
              } else {
                val updatedLogs = logs ++ logStepErrorResult(currentStep, e, RED) ++ logNonExecutedStep(steps.tail)
                buildFailedRunSteps(steps, currentStep, e, updatedLogs)
              }

            case Xor.Right(currentSession) ⇒
              val updatedLogs = if (currentStep.show)
                logs :+ ColoredLogInstruction(s"   ${currentStep.title}", GREEN)
              else logs
              runSteps(steps.tail, currentSession, eventuallyConf.consume(executionTime), snapshot, updatedLogs)
          }
      }
  }

  private def runStepAction[A](step: ExecutableStep[A])(implicit session: Session): Xor[CornichonError, Session] =
    Try { step.action(session) } match {
      case Success((newSession, stepAssertion)) ⇒ runStepPredicate(step, newSession, stepAssertion)
      case Failure(e) ⇒
        e match {
          case ce: CornichonError ⇒ left(ce)
          case _                  ⇒ left(StepExecutionError(e))
        }
    }

  private def runStepPredicate[A](step: ExecutableStep[A], newSession: Session, stepAssertion: StepAssertion[A]): Xor[CornichonError, Session] = {
    val succeedAsExpected = stepAssertion.isSuccess && !step.negate
    val failedAsExpected = !stepAssertion.isSuccess && step.negate

    if (succeedAsExpected || failedAsExpected) right(newSession)
    else
      stepAssertion match {
        case SimpleStepAssertion(expected, actual) ⇒
          left(StepAssertionError(expected, actual, step.negate))
        case DetailedStepAssertion(expected, actual, details) ⇒
          left(DetailedStepAssertionError(actual, details))
      }
  }

  private def logStepErrorResult(step: ExecutableStep[_], error: CornichonError, ansiColor: String): Seq[LogInstruction] =
    Seq(ColoredLogInstruction(s"   ${step.title} *** FAILED ***", ansiColor)) ++ error.msg.split('\n').map { m ⇒
      ColoredLogInstruction(s"   $m", ansiColor)
    }

  private def logNonExecutedStep(steps: Seq[Step]): Seq[LogInstruction] =
    steps.collect { case e: ExecutableStep[_] ⇒ e }
      .filter(_.show).map { step ⇒
        ColoredLogInstruction(s"   ${step.title}", CYAN)
      }

  private def buildFailedRunSteps(steps: Seq[Step], currentStep: ExecutableStep[_], e: CornichonError, logs: Seq[LogInstruction]): FailedRunSteps = {
    val failedStep = FailedStep(currentStep, e)
    val notExecutedStep = steps.tail.collect { case ExecutableStep(title, _, _, _) ⇒ title }
    FailedRunSteps(failedStep, notExecutedStep, logs)
  }

  private case class RollbackSnapshot(steps: Seq[Step], session: Session)
}