package com.github.agourlay.cornichon.steps.wrapped

import cats.data.StateT
import cats.syntax.foldable._
import cats.effect.IO
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import fs2.Stream

import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

case class ConcurrentlyStep(nested: List[Step], maxTime: FiniteDuration) extends WrapperStep {

  val title = s"Concurrently block with maxTime '$maxTime'"

  override val stateUpdate: StepState = StateT { runState =>
    val nestedRunState = runState.nestedContext
    val initialDepth = runState.depth
    Stream.iterable[IO, Step](nested)
      .mapAsyncUnordered(nested.size)(s => ScenarioRunner.runStepsShortCircuiting(s :: Nil, nestedRunState))
      .interruptAfter(maxTime)
      .compile
      .toList
      .timed
      .flatMap {
        case (executionTime, results) =>
          if (results.size != nested.size) {
            val error = ConcurrentlyTimeout(nested.size, results.size)
            val errorState = runState.recordLog(failedTitleLog(initialDepth)).recordLog(FailureLogInstruction(error.renderedMessage, initialDepth, Some(executionTime)))
            val failedStep = FailedStep.fromSingle(this, error)
            IO.pure(errorState -> Left(failedStep))
          } else {
            val failedStepRuns = results.collect { case (s, r @ Left(_)) => (s, r) }
            failedStepRuns.headOption.fold[IO[(RunState, Either[FailedStep, Done])]] {
              val successStepsRun = results.collect { case (s, r @ Right(_)) => (s, r) }
              val allRunStates = successStepsRun.map(_._1)
              //TODO all logs should be merged?
              // all runs were successful, we pick the first one for the logs
              val firstStateLog = allRunStates.head.logStack
              val wrappedLogStack = SuccessLogInstruction(s"Concurrently block succeeded", initialDepth, Some(executionTime)) +: firstStateLog :+ successTitleLog(initialDepth)
              // TODO merge all sessions together - require diffing Sessions or it produces a huge map full of duplicate as they all started from the same.
              val updatedSession = allRunStates.head.session
              // merge all cleanups steps
              val allCleanupSteps = allRunStates.foldMap(_.cleanupSteps)
              val successState = runState.withSession(updatedSession).recordLogStack(wrappedLogStack).registerCleanupSteps(allCleanupSteps)
              IO.pure((successState, rightDone))
            } {
              case (s, failedStep) =>
                val ratio = s"'${failedStepRuns.size}/${nested.size}' run(s)"
                val wrapLogStack = FailureLogInstruction(s"Concurrently block failed for $ratio", initialDepth) +: s.logStack :+ failedTitleLog(initialDepth)
                IO.pure((runState.mergeNested(s, wrapLogStack), failedStep))
            }
          }
      }.handleError {
        case NonFatal(e) =>
          val failedStep = FailedStep.fromSingle(this, ConcurrentlyError(e))
          (runState.recordLog(failedTitleLog(initialDepth)), Left(failedStep))
      }
  }
}

case class ConcurrentlyTimeout(total: Int, success: Int) extends CornichonError {
  lazy val baseErrorMessage = s"Concurrently block did not reach completion in time: $success/$total finished"
}

case class ConcurrentlyError(cause: Throwable) extends CornichonError {
  lazy val baseErrorMessage = "Concurrently block has thrown an error"
  override val causedBy = CornichonError.fromThrowable(cause) :: Nil
}
