package com.github.agourlay.cornichon.steps.wrapped

import java.util.concurrent.TimeUnit

import akka.actor.Scheduler
import cats.data.NonEmptyList
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.util.Timing._

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration

case class RepeatDuringStep(nested: List[Step], duration: FiniteDuration) extends WrapperStep {
  val title = s"Repeat block during '$duration'"

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext, scheduler: Scheduler) = {

    val initialDepth = initialRunState.depth

    def repeatStepsDuring(runState: RunState, duration: FiniteDuration, retriesNumber: Long): Future[(Long, RunState, Either[FailedStep, Done])] = {
      withDuration {
        // reset logs at each loop to have the possibility to not aggregate in failure case
        engine.runSteps(runState.resetLogs)
      }.flatMap {
        case (run, executionTime) ⇒

          val (repeatedOnceMore, res) = run
          val remainingTime = duration - executionTime
          res match {
            case Right(done) ⇒
              val successState = runState.withSession(repeatedOnceMore.session).appendLogsFrom(repeatedOnceMore)
              if (remainingTime.gt(FiniteDuration(0, TimeUnit.MILLISECONDS)))
                repeatStepsDuring(successState, remainingTime, retriesNumber + 1)
              else
                // In case of success all logs are returned but they are not printed by default.
                Future.successful(retriesNumber, successState, rightDone)
            case Left(failedStep) ⇒
              // In case of failure only the logs of the last run are shown to avoid giant traces.
              Future.successful(retriesNumber, repeatedOnceMore, Left(failedStep))
          }
      }
    }

    withDuration {
      val bootstrapRepeatState = initialRunState.forNestedSteps(nested)
      repeatStepsDuring(bootstrapRepeatState, duration, 0)
    }.map {
      case (run, executionTime) ⇒

        val (retries, repeatedRunState, report) = run

        val withSession = initialRunState.withSession(repeatedRunState.session)

        report match {
          case Right(done) ⇒
            val fullLogs = successTitleLog(initialDepth) +: repeatedRunState.logs :+ SuccessLogInstruction(s"Repeat block during '$duration' succeeded after '$retries' retries", initialDepth, Some(executionTime))
            (withSession.appendLogs(fullLogs), rightDone)
          case Left(failedStep) ⇒
            val fullLogs = failedTitleLog(initialDepth) +: repeatedRunState.logs :+ FailureLogInstruction(s"Repeat block during '$duration' failed after being retried '$retries' times", initialDepth, Some(executionTime))
            val artificialFailedStep = FailedStep.fromSingle(failedStep.step, RepeatDuringBlockContainFailedSteps(duration, failedStep.errors))
            (withSession.appendLogs(fullLogs), Left(artificialFailedStep))
        }
    }
  }
}

case class RepeatDuringBlockContainFailedSteps(duration: FiniteDuration, errors: NonEmptyList[CornichonError]) extends CornichonError {
  val baseErrorMessage = s"RepeatDuring block failed before '$duration'"
  override val causedBy = Some(errors)
}
