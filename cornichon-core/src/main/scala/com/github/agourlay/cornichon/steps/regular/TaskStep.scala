package com.github.agourlay.cornichon.steps.regular

import cats.data.NonEmptyList
import cats.syntax.either._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._
import monix.eval.Task

import scala.concurrent.duration.Duration

// Check how to not hard-code monix.Task using cats-effect to make it public
// https://github.com/agourlay/cornichon/issues/145
private[cornichon] case class TaskStep(title: String, effect: Session ⇒ Task[Either[CornichonError, Session]], show: Boolean = true) extends ValueStep[Session] {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def run(initialRunState: RunState) =
    effect(initialRunState.session).map(_.leftMap(NonEmptyList.one))

  override def onError(errors: NonEmptyList[CornichonError], initialRunState: RunState) =
    errorsToFailureStep(this, initialRunState.depth, errors)

  override def onSuccess(result: Session, initialRunState: RunState, executionTime: Duration) =
    (successLog(title, initialRunState.depth, show, executionTime), Some(result))
}