package com.github.agourlay.cornichon.core

import cats.data.Xor
import cats.data.Xor.{ left, right }
import spray.json.JsValue

import scala.annotation.tailrec
import scala.util._

class Engine(resolver: Resolver) {

  def runStep[A](step: Step[A])(implicit session: Session): Xor[CornichonError, Session] = {
    Try {
      val (res, newSession) = step.action(session)
      val resolvedExpected: A = step.expected match {
        case s: String  ⇒ resolver.fillPlaceholder(s)(newSession.content).fold(error ⇒ throw error, v ⇒ v).asInstanceOf[A]
        case j: JsValue ⇒ resolver.fillPlaceholder(j)(newSession.content).fold(error ⇒ throw error, v ⇒ v).asInstanceOf[A]
        case _          ⇒ step.expected
      }
      (res, resolvedExpected, newSession)
    } match {
      case Success((res, expected, newSession)) ⇒ runStepPredicate(step.title, res, expected, newSession)
      case Failure(e) ⇒
        e match {
          case KeyNotFoundInSession(key) ⇒ left(SessionError(step.title, key))
          case _                         ⇒ left(StepExecutionError(step.title, e))
        }
    }
  }

  def runStepPredicate[A](title: String, actual: A, expected: A, newSession: Session): Xor[CornichonError, Session] = {
    StepAssertionResult(actual == expected, expected, actual) match {
      case StepAssertionResult(true, _, _)              ⇒ right(newSession)
      case StepAssertionResult(false, expected, actual) ⇒ left(StepAssertionError(title, expected, actual))
    }
  }

  def runScenario(scenario: Scenario)(session: Session): Xor[FailedScenarioReport, SuccessScenarioReport] = {
    @tailrec
    def loop(steps: Seq[Step[_]], session: Session): Xor[FailedScenarioReport, SuccessScenarioReport] = {
      if (steps.isEmpty) right(SuccessScenarioReport(scenario.name))
      else {
        val currentStep = steps.head
        runStep(currentStep)(session) match {
          case Xor.Left(e) ⇒
            val failedStep = FailedStep(steps.head.title, e)
            val successStep = scenario.steps.takeWhile(_ != steps.head).map(_.title)
            val notExecutedStep = steps.tail.map(_.title)
            left(FailedScenarioReport(scenario.name, failedStep, successStep, notExecutedStep))
          case Xor.Right(currentSession) ⇒
            loop(steps.tail, currentSession)
        }
      }
    }
    loop(scenario.steps, session)
  }
}