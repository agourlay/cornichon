package com.github.agourlay.cornichon.core

import cats.data.Xor
import cats.data.Xor.{ left, right }

import scala.annotation.tailrec
import scala.util._

class Engine(resolver: Resolver) {

  def runStep[A](step: Step[A])(implicit session: Session): Xor[CornichonError, Session] = {
    for {
      newTitle ← resolver.fillPlaceHolder(step.title)(session.content)
      stepResult ← runStepInstruction(step.copy(title = newTitle), session)
    } yield stepResult
  }

  def runStepInstruction[A](step: Step[A], session: Session): Xor[CornichonError, Session] = {
    Try {
      step.instruction(session)
    } match {
      case Success((res, newSession)) ⇒ runStepPredicate(step, res, newSession)
      case Failure(e) ⇒
        e match {
          case KeyNotFoundInSession(key) ⇒ left(SessionError(step.title, key))
          case _                         ⇒ left(StepExecutionError(step.title, e))
        }
    }
  }

  def runStepPredicate[A](step: Step[A], res: A, newSession: Session): Xor[CornichonError, Session] = {
    Try {
      step.assertion(res)
    } match {
      case Success(result) if result  ⇒ right(newSession)
      case Success(result) if !result ⇒ left(StepAssertionError(step.title, res))
      case Failure(e)                 ⇒ left(StepPredicateError(step.title, e))
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