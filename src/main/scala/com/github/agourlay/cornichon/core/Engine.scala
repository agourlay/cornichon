package com.github.agourlay.cornichon.core

import cats.data.Xor
import cats.data.Xor.{ left, right }
import spray.json.JsValue

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.util._
import Console._

class Engine extends CornichonLogger {

  def runStep[A](step: ExecutableStep[A])(implicit session: Session): Xor[CornichonError, Session] =
    Try {
      val (res, newSession) = step.action(session)
      val resolvedExpected: A = step.expected match {
        case s: String  ⇒ Resolver.fillPlaceholder(s)(newSession.content).fold(error ⇒ throw error, v ⇒ v).asInstanceOf[A]
        case j: JsValue ⇒ Resolver.fillPlaceholder(j)(newSession.content).fold(error ⇒ throw error, v ⇒ v).asInstanceOf[A]
        case _          ⇒ step.expected
      }
      (res, resolvedExpected, newSession)
    } match {
      case Success((res, expected, newSession)) ⇒ runStepPredicate(step.title, res, expected, newSession)
      case Failure(e) ⇒
        e match {
          case ce: CornichonError ⇒ left(ce)
          case _                  ⇒ left(StepExecutionError(step.title, e))
        }
    }

  def runStepPredicate[A](title: String, actual: A, expected: A, newSession: Session): Xor[CornichonError, Session] =
    StepAssertionResult(actual == expected, expected, actual) match {
      case StepAssertionResult(true, _, _) ⇒
        log.info(GREEN + s"   $title" + RESET)
        right(newSession)
      case StepAssertionResult(false, exp, act) ⇒
        val error = StepAssertionError(exp, act)
        log.error(RED + s"   $title *** FAILED ***" + RESET)
        log.error(RED + s"${error.msg}" + RESET)
        left(error)
    }

  def runScenario(scenario: Scenario)(session: Session): ScenarioReport = {
    @tailrec
    def loop(steps: Seq[Step], session: Session, eventuallyConf: EventuallyConf, snapshot: Option[RollbackSnapshot]): ScenarioReport = {
      if (steps.isEmpty)
        SuccessScenarioReport(
          scenarioName = scenario.name,
          successSteps = scenario.steps.collect { case ExecutableStep(title, _, _) ⇒ title }
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
                  Thread.sleep(eventuallyConf.interval.toMillis)
                  loop(snapshot.get.steps, snapshot.get.session, eventuallyConf.consume(executionTime + eventuallyConf.interval), snapshot)
                } else buildFailedScenarioReport(scenario, steps, currentStep, e)
              case Xor.Right(currentSession) ⇒
                loop(steps.tail, currentSession, eventuallyConf.consume(executionTime), snapshot)
            }
          case EventuallyStart(conf) ⇒
            log.info(s"Eventually bloc with maxDuration = ${conf.maxTime} and interval = ${conf.interval}")
            loop(steps.tail, session, conf, Some(RollbackSnapshot(steps.tail, session)))
          case EventuallyStop(conf) ⇒
            log.info(s"Eventually bloc succeeded in ${conf.maxTime.toSeconds - eventuallyConf.maxTime.toSeconds} sec.")
            loop(steps.tail, session, EventuallyConf.empty, None)
        }
    }
    loop(scenario.steps, session, EventuallyConf.empty, None)
  }

  def buildFailedScenarioReport(scenario: Scenario, steps: Seq[Step], currentStep: ExecutableStep[_], e: CornichonError): FailedScenarioReport = {
    val failedStep = FailedStep(currentStep.title, e)
    val successStep = scenario.steps.takeWhile(_ != steps.head).collect { case ExecutableStep(title, _, _) ⇒ title }
    val notExecutedStep = steps.tail.collect { case ExecutableStep(title, _, _) ⇒ title }
    FailedScenarioReport(scenario.name, failedStep, successStep, notExecutedStep)
  }

  case class RollbackSnapshot(steps: Seq[Step], session: Session)
}