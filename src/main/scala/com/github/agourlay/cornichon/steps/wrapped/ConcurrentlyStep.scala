package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.Duration
import scala.util.{ Failure, Success, Try }

case class ConcurrentlyStep(nested: Vector[Step], factor: Int, maxTime: Duration) extends WrapperStep {
  require(factor > 0, "concurrently block must contain a positive factor")
  val title = s"Concurrently block with factor '$factor' and maxTime '$maxTime'"

  def run(engine: Engine, nextSteps: Vector[Step], session: Session, logs: Vector[LogInstruction], depth: Int)(implicit ec: ExecutionContext) = {
    val updatedLogs = logs :+ DefaultLogInstruction(title, depth)
    val start = System.nanoTime
    val f = Future.traverse(List.fill(factor)(nested)) { steps ⇒
      Future { engine.runSteps(steps, session, updatedLogs, depth + 1) }
    }

    val results = Try { Await.result(f, maxTime) } match {
      case Success(s) ⇒ s
      case Failure(e) ⇒ List(engine.buildFailedRunSteps(this, nextSteps, ConcurrentlyTimeout, updatedLogs, session))
    }

    val failedStepRun = results.collectFirst { case f @ FailedRunSteps(_, _, _, _) ⇒ f }
    failedStepRun.fold {
      val executionTime = Duration.fromNanos(System.nanoTime - start)
      val successStepsRun = results.collect { case s @ SuccessRunSteps(_, _) ⇒ s }
      val updatedSession = successStepsRun.head.session
      val updatedLogs = successStepsRun.head.logs :+ SuccessLogInstruction(s"Concurrently block with factor '$factor' succeeded", depth, Some(executionTime))
      engine.runSteps(nextSteps, updatedSession, updatedLogs, depth)
    } { f ⇒
      f.copy(logs = (f.logs :+ FailureLogInstruction(s"Concurrently block failed", depth)) ++ engine.logNonExecutedStep(nextSteps, depth))
    }
  }
}
