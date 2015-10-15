package com.github.agourlay.cornichon.core

import cats.data.Xor
import cats.data.Xor.{ left, right }

import scala.Console._
import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.util._

class Engine {

  def runStep[A](step: ExecutableStep[A])(implicit session: Session): Xor[CornichonError, Session] =
    Try { step.action(session) } match {
      case Success((newSession, stepAssertion)) ⇒ runStepPredicate(step, newSession, stepAssertion)
      case Failure(e) ⇒
        e match {
          case ce: CornichonError ⇒ left(ce)
          case _                  ⇒ left(StepExecutionError(e))
        }
    }

  def runStepPredicate[A](step: ExecutableStep[A], newSession: Session, stepAssertion: StepAssertion[A]): Xor[CornichonError, Session] = {
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

  def runScenario(scenario: Scenario)(session: Session): ScenarioReport = {
    @tailrec
    def loop(steps: Seq[Step], session: Session, eventuallyConf: EventuallyConf, snapshot: Option[RollbackSnapshot], logs: Seq[LogInstruction]): ScenarioReport = {
      if (steps.isEmpty)
        SuccessScenarioReport(
          scenarioName = scenario.name,
          successSteps = scenario.steps.collect { case ExecutableStep(title, _, _, _) ⇒ title },
          logs = logs
        )
      else
        steps.head match {
          case DebugStep(message) ⇒
            val updatedLogs = logs :+ ColoredLogInstruction(message(session), CYAN)
            loop(steps.tail, session, eventuallyConf, snapshot, updatedLogs)

          case EventuallyStart(conf) ⇒
            val updatedLogs = logs :+ DefaultLogInstruction(s"   Eventually bloc with maxDuration = ${conf.maxTime} and interval = ${conf.interval}")
            loop(steps.tail, session, conf, Some(RollbackSnapshot(steps.tail, session)), updatedLogs)

          case EventuallyStop(conf) ⇒
            val updatedLogs = logs :+ DefaultLogInstruction(s"   Eventually bloc succeeded in ${conf.maxTime.toSeconds - eventuallyConf.maxTime.toSeconds} sec.")
            loop(steps.tail, session, EventuallyConf.empty, None, updatedLogs)

          case currentStep: ExecutableStep[_] ⇒
            val now = System.nanoTime
            val stepResult = runStep(currentStep)(session)
            val executionTime = Duration.fromNanos(System.nanoTime - now)
            stepResult match {
              case Xor.Left(e) ⇒
                val remainingTime = eventuallyConf.maxTime - executionTime
                if (remainingTime.gt(Duration.Zero)) {
                  val updatedLogs = logs ++ logStepErrorResult(currentStep, e, CYAN)
                  Thread.sleep(eventuallyConf.interval.toMillis)
                  loop(snapshot.get.steps, snapshot.get.session, eventuallyConf.consume(executionTime + eventuallyConf.interval), snapshot, updatedLogs)
                } else {
                  val updatedLogs = logs ++ logStepErrorResult(currentStep, e, RED) ++ logNonExecutedStep(steps.tail)
                  buildFailedScenarioReport(scenario, steps, currentStep, e, updatedLogs)
                }

              case Xor.Right(currentSession) ⇒
                val updatedLogs = if (currentStep.show)
                  logs :+ ColoredLogInstruction(s"   ${currentStep.title}", GREEN)
                else logs
                loop(steps.tail, currentSession, eventuallyConf.consume(executionTime), snapshot, updatedLogs)
            }
        }
    }
    val initLogs = Seq(DefaultLogInstruction(s"Scenario : ${scenario.name}"))
    loop(scenario.steps, session, EventuallyConf.empty, None, initLogs)
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

  def buildFailedScenarioReport(scenario: Scenario, steps: Seq[Step], currentStep: ExecutableStep[_], e: CornichonError, logs: Seq[LogInstruction]): FailedScenarioReport = {
    val failedStep = FailedStep(currentStep.title, e)
    val successStep = scenario.steps.takeWhile(_ != steps.head).collect { case ExecutableStep(title, _, _, _) ⇒ title }
    val notExecutedStep = steps.tail.collect { case ExecutableStep(title, _, _, _) ⇒ title }
    FailedScenarioReport(scenario.name, failedStep, successStep, notExecutedStep, logs)
  }

  case class RollbackSnapshot(steps: Seq[Step], session: Session)
}