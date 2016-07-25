package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.Duration
import scala.util.{ Failure, Success, Try }

case class ConcurrentlyStep(nested: Vector[Step], factor: Int, maxTime: Duration) extends WrapperStep {

  require(factor > 0, "concurrently block must contain a positive factor")

  val title = s"Concurrently block with factor '$factor' and maxTime '$maxTime'"

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {
    val start = System.nanoTime
    val f = Future.traverse(List.fill(factor)(nested)) { steps ⇒
      Future { engine.runSteps(steps, session, Vector.empty, depth + 1) }
    }

    val results = Try { Await.result(f, maxTime) } match {
      case Success(s) ⇒
        s
      case Failure(e) ⇒
        val failedStep = FailedStep(this, ConcurrentlyTimeout)
        List((session, FailureStepsResult(failedStep, Vector(failedTitleLog(depth)))))
    }

    // Only the first error report found is used in the logs.
    val failedStepRun = results.collectFirst { case (s, r @ FailureStepsResult(_, _)) ⇒ (s, r) }
    failedStepRun.fold[(Session, StepsResult)] {
      val executionTime = Duration.fromNanos(System.nanoTime - start)
      val successStepsRun = results.collect { case (s, r @ SuccessStepsResult(_)) ⇒ (s, r) }
      //TODO all sessions should be merged?
      val updatedSession = successStepsRun.head._1
      //TODO all logs should be merged?
      val updatedLogs = successTitleLog(depth) +: successStepsRun.head._2.logs :+ SuccessLogInstruction(s"Concurrently block with factor '$factor' succeeded", depth, Some(executionTime))
      (updatedSession, SuccessStepsResult(updatedLogs))
    } {
      case (s, f) ⇒
        (s, f.copy(logs = failedTitleLog(depth) +: f.logs :+ FailureLogInstruction(s"Concurrently block failed", depth)))
    }
  }
}
