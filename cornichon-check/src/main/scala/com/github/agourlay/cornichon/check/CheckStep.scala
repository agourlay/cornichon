package com.github.agourlay.cornichon.check

import cats.data.NonEmptyList
import com.github.agourlay.cornichon.core.Engine.{ errorsToFailureStep, successLog }
import com.github.agourlay.cornichon.core._
import monix.eval.Task

import scala.concurrent.duration.Duration

case class CheckStep(model: Model) extends ValueStep[Done] {

  val title = s"Checking model $model"

  def setTitle(newTitle: String) = this

  def run(initialRunState: RunState): Task[Either[NonEmptyList[CornichonError], Done]] =
    CheckEngine.run(initialRunState, model)

  def onError(errors: NonEmptyList[CornichonError], initialRunState: RunState): (Vector[LogInstruction], FailedStep) =
    errorsToFailureStep(this, initialRunState.depth, errors)

  def onSuccess(result: Done, initialRunState: RunState, executionTime: Duration): (Option[LogInstruction], Option[Session]) =
    (successLog(title, initialRunState.depth, true, executionTime), None)

}
