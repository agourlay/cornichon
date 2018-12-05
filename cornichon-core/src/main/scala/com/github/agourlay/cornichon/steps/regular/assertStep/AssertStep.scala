package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.data._
import cats.syntax.validated._
import cats.syntax.apply._

import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import monix.eval.Task

import scala.concurrent.duration.Duration

case class AssertStep(title: String, action: Session ⇒ Assertion, show: Boolean = true) extends LogValueStep[Done] {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def run(initialRunState: RunState): Task[Either[NonEmptyList[CornichonError], Done]] = {
    val assertion = action(initialRunState.session)
    Task.now(assertion.validated.toEither)
  }

  override def onError(errors: NonEmptyList[CornichonError], initialRunState: RunState): (List[LogInstruction], FailedStep) =
    errorsToFailureStep(this, initialRunState.depth, errors)

  override def logOnSuccess(result: Done, initialRunState: RunState, executionTime: Duration): LogInstruction =
    successLog(title, initialRunState.depth, show, executionTime)
}

trait Assertion { self ⇒
  def validated: ValidatedNel[CornichonError, Done]

  def and(other: Assertion): Assertion = new Assertion {
    val validated = self.validated *> other.validated
  }

  def andAll(others: Seq[Assertion]): Assertion = new Assertion {
    val validated = others.fold(self)(_ and _).validated
  }

  def or(other: Assertion): Assertion = new Assertion {
    val validated =
      if (self.validated.isValid || other.validated.isValid)
        validDone
      else
        self.validated *> other.validated
  }
}

object Assertion {

  val alwaysValid: Assertion = new Assertion { val validated = validDone }
  def failWith(error: String) = new Assertion { val validated = BasicError(error).invalidNel }
  def failWith(error: CornichonError) = new Assertion { val validated = error.invalidNel }

  def either(v: Either[CornichonError, Assertion]) = v.fold(e ⇒ failWith(e), identity)

  def all(assertions: Seq[Assertion]): Assertion = assertions.reduce((acc, assertion) ⇒ acc.and(assertion))
  def any(assertions: Seq[Assertion]): Assertion = assertions.reduce((acc, assertion) ⇒ acc.or(assertion))
}