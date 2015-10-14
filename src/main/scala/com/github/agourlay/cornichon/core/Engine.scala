package com.github.agourlay.cornichon.core

import cats.data.Xor
import cats.data.Xor.{ left, right }

import scala.Console._
import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.util._

class Engine extends CornichonLogger {

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

    if (succeedAsExpected || failedAsExpected) {
      if (step.show) logger.info(GREEN + s"   ${step.title}" + RESET)
      right(newSession)
    } else
      stepAssertion match {
        case SimpleStepAssertion(expected, actual) ⇒
          left(StepAssertionError(expected, actual, step.negate))
        case DetailedStepAssertion(expected, actual, details) ⇒
          left(DetailedStepAssertionError(actual, details))
      }

  }

  def runScenario(scenario: Scenario)(session: Session): ScenarioReport = {
    @tailrec
    def loop(steps: Seq[Step], session: Session, eventuallyConf: EventuallyConf, snapshot: Option[RollbackSnapshot]): ScenarioReport = {
      if (steps.isEmpty)
        SuccessScenarioReport(
          scenarioName = scenario.name,
          successSteps = scenario.steps.collect { case ExecutableStep(title, _, _, _) ⇒ title }
        )
      else
        steps.head match {
          case currentStep: ExecutableStep[_] ⇒
            val now = System.nanoTime
            val stepResult = runStep(currentStep)(session)
            val executionTime = Duration.fromNanos(System.nanoTime - now)
            stepResult match {
              case Xor.Left(e) ⇒
                val remainingTime = eventuallyConf.maxTime - executionTime
                if (remainingTime.gt(Duration.Zero)) {
                  logStepErrorResult(currentStep, e, CYAN)
                  Thread.sleep(eventuallyConf.interval.toMillis)
                  loop(snapshot.get.steps, snapshot.get.session, eventuallyConf.consume(executionTime + eventuallyConf.interval), snapshot)
                } else {
                  logStepErrorResult(currentStep, e, RED)
                  logNonExecutedStep(steps.tail)
                  buildFailedScenarioReport(scenario, steps, currentStep, e)
                }
              case Xor.Right(currentSession) ⇒
                loop(steps.tail, currentSession, eventuallyConf.consume(executionTime), snapshot)
            }
          case EventuallyStart(conf) ⇒
            logger.info(s"   Eventually bloc with maxDuration = ${conf.maxTime} and interval = ${conf.interval}")
            loop(steps.tail, session, conf, Some(RollbackSnapshot(steps.tail, session)))
          case EventuallyStop(conf) ⇒
            logger.info(s"   Eventually bloc succeeded in ${conf.maxTime.toSeconds - eventuallyConf.maxTime.toSeconds} sec.")
            loop(steps.tail, session, EventuallyConf.empty, None)
        }
    }
    loop(scenario.steps, session, EventuallyConf.empty, None)
  }

  private def logStepErrorResult(step: ExecutableStep[_], error: CornichonError, ansiColor: String): Unit = {
    logger.error(ansiColor + s"   ${step.title} *** FAILED ***" + RESET)
    error.msg.split('\n').foreach { m ⇒
      logger.error(ansiColor + s"   $m" + RESET)
    }
  }

  private def logNonExecutedStep(steps: Seq[Step]): Unit = {
    steps.collect {
      case e: ExecutableStep[_] ⇒ e
    }.filter(_.show).foreach { step ⇒
      logger.info(CYAN + s"   ${step.title}" + RESET)
    }
  }

  def buildFailedScenarioReport(scenario: Scenario, steps: Seq[Step], currentStep: ExecutableStep[_], e: CornichonError): FailedScenarioReport = {
    val failedStep = FailedStep(currentStep.title, e)
    val successStep = scenario.steps.takeWhile(_ != steps.head).collect { case ExecutableStep(title, _, _, _) ⇒ title }
    val notExecutedStep = steps.tail.collect { case ExecutableStep(title, _, _, _) ⇒ title }
    FailedScenarioReport(scenario.name, failedStep, successStep, notExecutedStep)
  }

  case class RollbackSnapshot(steps: Seq[Step], session: Session)
}