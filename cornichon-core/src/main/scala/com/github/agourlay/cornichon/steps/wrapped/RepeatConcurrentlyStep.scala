package com.github.agourlay.cornichon.steps.wrapped

import cats.data.StateT
import cats.syntax.foldable._
import cats.effect.IO
import cats.syntax.either._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import fs2.Stream

import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

case class RepeatConcurrentlyStep(times: Int, nested: List[Step], parallelism: Int, maxTime: FiniteDuration) extends WrapperStep {
  require(parallelism > 0, "repeat concurrently block must contain a positive 'parallelism' factor")
  require(times > 0, "repeat concurrently block must contain a positive 'times' factor")
  require(times >= parallelism, "repeat concurrently block must contain a 'parallelism' factor <= to the number of repeat 'times'")

  val title = s"Repeat concurrently block '$times' times with parallel factor '$parallelism' and maxTime '$maxTime'"

  override val stateUpdate: StepState = StateT { runState =>
    val nestedRunState = runState.nestedContext
    val initialDepth = runState.depth
    Stream.iterable[IO, Done](List.fill(times)(Done))
      .mapAsyncUnordered(parallelism)(_ => ScenarioRunner.runStepsShortCircuiting(nested, nestedRunState))
      .interruptAfter(maxTime)
      .compile
      .toList
      .timed
      .flatMap {
        case (executionTime, results) =>
          if (results.size != times) {
            val error = RepeatConcurrentlyTimeout(times, results.size)
            val errorState = runState.recordLog(failedTitleLog(initialDepth)).recordLog(FailureLogInstruction(error.renderedMessage, initialDepth, Some(executionTime)))
            val failedStep = FailedStep.fromSingle(this, error)
            IO.pure(errorState -> failedStep.asLeft)
          } else {
            val failedStepRuns = results.collect { case (s, r @ Left(_)) => (s, r) }
            failedStepRuns.headOption.fold[IO[(RunState, Either[FailedStep, Done])]] {
              val successStepsRun = results.collect { case (s, r @ Right(_)) => (s, r) }
              val allRunStates = successStepsRun.map(_._1)
              //TODO all logs should be merged?
              // all runs were successful, we pick the first one for the logs
              val firstStateLog = allRunStates.head.logStack
              val wrappedLogStack = SuccessLogInstruction(s"Repeat concurrently block succeeded", initialDepth, Some(executionTime)) +: firstStateLog :+ successTitleLog(initialDepth)
              // TODO merge all sessions together - require diffing Sessions otherwise it produces a huge map full of duplicate as they all started from the same.
              val updatedSession = allRunStates.head.session
              // merge all cleanups steps
              val allCleanupSteps = allRunStates.foldMap(_.cleanupSteps)
              val successState = runState.withSession(updatedSession).recordLogStack(wrappedLogStack).registerCleanupSteps(allCleanupSteps)
              IO.pure(successState -> rightDone)
            } {
              case (s, failedStep) =>
                val ratio = s"'${failedStepRuns.size}/$times' run(s)"
                val wrapLogStack = FailureLogInstruction(s"Repeat concurrently block failed for $ratio", initialDepth) +: s.logStack :+ failedTitleLog(initialDepth)
                IO.pure(runState.mergeNested(s, wrapLogStack) -> failedStep)
            }
          }
      }.handleError {
        case NonFatal(e) =>
          val failedStep = FailedStep.fromSingle(this, RepeatConcurrentlyError(e))
          (runState.recordLog(failedTitleLog(initialDepth)), failedStep.asLeft)
      }
  }
}

case class RepeatConcurrentlyTimeout(times: Int, success: Int) extends CornichonError {
  lazy val baseErrorMessage = s"Repeat concurrently block did not reach completion in time: $success/$times finished"
}

case class RepeatConcurrentlyError(cause: Throwable) extends CornichonError {
  lazy val baseErrorMessage = "Repeat concurrently block has thrown an error"
  override val causedBy = CornichonError.fromThrowable(cause) :: Nil
}
