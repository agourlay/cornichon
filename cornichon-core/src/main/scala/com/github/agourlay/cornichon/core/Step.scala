package com.github.agourlay.cornichon.core

import cats.data.NonEmptyList
import monix.execution.Scheduler
import com.github.agourlay.cornichon.core.Done._
import monix.eval.Task

import scala.concurrent.duration.Duration

sealed trait Step {
  def title: String
  def setTitle(newTitle: String): Step
  def run(engine: Engine)(initialRunState: RunState): Task[(RunState, FailedStep Either Done)]
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

//Step that delegate the execution of nested steps
trait SimpleWrapperStep extends Step {

  //Run nested with initialRunState.forNestedSteps(nested)
  def nestedToRun: List[Step]

  def onNestedError(failedStep: FailedStep, resultRunState: RunState, initialRunState: RunState, executionTime: Duration): (RunState, FailedStep)

  def onNestedSuccess(resultRunState: RunState, initialRunState: RunState, executionTime: Duration): RunState

  def run(engine: Engine)(initialRunState: RunState) = {
    val nestedStartingState = initialRunState.forNestedSteps(nestedToRun)
    val now = System.nanoTime
    engine.runSteps(nestedStartingState).map {
      case (resState, Left(failedStep)) ⇒
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        val (finalState, fs) = onNestedError(failedStep, resState, initialRunState, executionTime)
        (finalState, Left(fs))

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

//Step that explictly control the execution of nested steps
trait WrapperStep extends Step {
  // Only used to enforce the shape of the concrete type
  def nested: List[Step]

  // Without effect by default - wrapper steps usually build dynamically their title
  def setTitle(newTitle: String) = this
  def failedTitleLog(depth: Int) = FailureLogInstruction(title, depth)
  def successTitleLog(depth: Int) = SuccessLogInstruction(title, depth)
}

