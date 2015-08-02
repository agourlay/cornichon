package com.github.agourlay.cornichon.core

import cats.data.Xor
import cats.data.Xor.{ left, right }

import scala.annotation.tailrec
import scala.util._

class Engine(resolver: Resolver) {

  def runStep[A](step: Step[A])(implicit session: Session): Xor[CornichonError, Session] = {
    for {
      newTitle ← resolver.fillPlaceHolder(step.title)(session.content)
      stepResult ← {
        val uStep = step.copy(title = newTitle)
        Try {
          uStep.instruction(session)
        } match {
          case Failure(e) ⇒
            e match {
              case KeyNotFoundInSession(key) ⇒ left(SessionError(step.title, key))
              case _                         ⇒ left(StepExecutionError(step.title, e))
            }
          case Success((res, newSession)) ⇒
            if (step.assertion(res))
              right(newSession)
            else
              left(StepAssertionError(step.title, res))
        }
      }
    } yield stepResult
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