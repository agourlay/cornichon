package com.github.agourlay.cornichon.core

import cats.data.NonEmptyList
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.steps.wrapped.FlatMapStep
import monix.eval.Task

import scala.concurrent.duration.Duration

sealed trait Step {
  def title: String
  def setTitle(newTitle: String): Step
  def run(engine: Engine)(initialRunState: RunState): Task[(RunState, FailedStep Either Done)]
  def chain(others: Session ⇒ List[Step]): Step = FlatMapStep(this, others)
}

//Step that produces a value
trait ValueStep[A] extends Step {

  def run(initialRunState: RunState): Task[NonEmptyList[CornichonError] Either A]

  def onError(errors: NonEmptyList[CornichonError], initialRunState: RunState): (Vector[LogInstruction], FailedStep)

  def onSuccess(result: A, initialRunState: RunState, executionTime: Duration): (Option[LogInstruction], Option[Session])

  def run(engine: Engine)(initialRunState: RunState) = {
    val now = System.nanoTime
    run(initialRunState).map {
      case Left(errors) ⇒
        val (logs, failedStep) = onError(errors, initialRunState)
        (initialRunState.appendLogs(logs), Left(failedStep))

      case Right(value) ⇒
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        val (log, session) = onSuccess(value, initialRunState, executionTime)
        val logState = log.map(initialRunState.appendLog).getOrElse(initialRunState)
        val logSessionState = session.map(logState.withSession).getOrElse(logState)
        (logSessionState, rightDone)
    }
  }
}

//Step that delegate the execution of nested steps and enable to decorate the nestedLogs
trait LogDecoratorStep extends Step {

  def nestedToRun: List[Step]

  def onNestedError(resultLogs: Vector[LogInstruction], depth: Int, executionTime: Duration): Vector[LogInstruction]

  def onNestedSuccess(resultLogs: Vector[LogInstruction], depth: Int, executionTime: Duration): Vector[LogInstruction]

  def run(engine: Engine)(initialRunState: RunState) = {
    val now = System.nanoTime
    engine.runSteps(nestedToRun, initialRunState.nestedContext).map {
      case (resState, l @ Left(_)) ⇒
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        val decoratedLogs = onNestedError(resState.logs, initialRunState.depth, executionTime)
        (initialRunState.mergeNested(resState, decoratedLogs), l)

      case (resState, r @ Right(_)) ⇒
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        val decoratedLogs = onNestedSuccess(resState.logs, initialRunState.depth, executionTime)
        (initialRunState.mergeNested(resState, decoratedLogs), r)
    }
  }

  // Without effect by default - wrapper steps usually build dynamically their title
  def setTitle(newTitle: String) = this
  def failedTitleLog(depth: Int) = FailureLogInstruction(title, depth)
  def successTitleLog(depth: Int) = SuccessLogInstruction(title, depth)
}

//Step that delegate the execution of nested steps and enable to inspect RunState and FailedStep
trait SimpleWrapperStep extends Step {

  def nestedToRun: List[Step]

  def onNestedError(failedStep: FailedStep, resultRunState: RunState, initialRunState: RunState, executionTime: Duration): (RunState, FailedStep)

  def onNestedSuccess(resultRunState: RunState, initialRunState: RunState, executionTime: Duration): RunState

  def run(engine: Engine)(initialRunState: RunState) = {
    val now = System.nanoTime
    engine.runSteps(nestedToRun, initialRunState.nestedContext).map {
      case (resState, l @ Left(failedStep)) ⇒
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        val (finalState, fs) = onNestedError(failedStep, resState, initialRunState, executionTime)
        (finalState, l)

      case (resState, Right(_)) ⇒
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        val finalState = onNestedSuccess(resState, initialRunState, executionTime)
        (finalState, rightDone)
    }
  }

  // Without effect by default - wrapper steps usually build dynamically their title
  def setTitle(newTitle: String) = this
  def failedTitleLog(depth: Int) = FailureLogInstruction(title, depth)
  def successTitleLog(depth: Int) = SuccessLogInstruction(title, depth)
}

//Step that gives full control over the execution of nested steps and their error reporting
trait WrapperStep extends Step {
  // Without effect by default - wrapper steps usually build dynamically their title
  def setTitle(newTitle: String) = this
  def failedTitleLog(depth: Int) = FailureLogInstruction(title, depth)
  def successTitleLog(depth: Int) = SuccessLogInstruction(title, depth)
}

