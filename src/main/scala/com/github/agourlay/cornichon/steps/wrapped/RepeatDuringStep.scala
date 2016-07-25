package com.github.agourlay.cornichon.steps.wrapped

import cats.data.Xor
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.core.Done._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import cats.data.Xor._

case class RepeatDuringStep(nested: Vector[Step], duration: Duration) extends WrapperStep {
  val title = s"Repeat block during '$duration'"

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {

    @tailrec
    def repeatStepsDuring(steps: Vector[Step], session: Session, duration: Duration, accLogs: Vector[LogInstruction], retriesNumber: Long, depth: Int): (Long, Session, Vector[LogInstruction], Xor[FailedStep, Done]) = {
      val ((newSession, logs, res), executionTime) = withDuration {
        engine.runSteps(steps, session, Vector.empty, depth)
      }
      val remainingTime = duration - executionTime
      res match {
        case Right(done) ⇒
          if (remainingTime.gt(Duration.Zero))
            repeatStepsDuring(steps, newSession, remainingTime, accLogs ++ logs, retriesNumber + 1, depth)
          else
            // In case of success all logs are returned but they are not printed by default.
            (retriesNumber, newSession, accLogs ++ logs, rightDone)
        case Left(failedStep) ⇒
          // In case of failure only the logs of the last run are shown to avoid giant traces.
          (retriesNumber, newSession, logs, left(failedStep))
      }
    }

    val (repeatRes, executionTime) = withDuration {
      repeatStepsDuring(nested, session, duration, Vector.empty, 0, depth + 1)
    }

    val (retries, newSession, logs, report) = repeatRes

    report match {
      case Right(done) ⇒
        val fullLogs = successTitleLog(depth) +: logs :+ SuccessLogInstruction(s"Repeat block during '$duration' succeeded after '$retries' retries", depth, Some(executionTime))
        (newSession, fullLogs, rightDone)
      case Left(failedStep) ⇒
        val fullLogs = failedTitleLog(depth) +: logs :+ FailureLogInstruction(s"Repeat block during '$duration' failed after being retried '$retries' times", depth, Some(executionTime))
        val artificialFailedStep = FailedStep(failedStep.step, RepeatDuringBlockContainFailedSteps)
        (newSession, fullLogs, left(artificialFailedStep))
    }
  }
}
