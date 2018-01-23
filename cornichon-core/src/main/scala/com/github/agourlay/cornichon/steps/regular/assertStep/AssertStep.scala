package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.data._
import cats.syntax.validated._
import cats.syntax.apply._
import cats.syntax.either._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import monix.eval.Task

import scala.concurrent.duration.Duration

case class AssertStep(title: String, action: Session ⇒ Assertion, show: Boolean = true) extends ValueStep[Done] {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def run(initialRunState: RunState) = {
    val assertion = action(initialRunState.session)
    Task.now(assertion.validated.toEither)
  }

  override def onError(errors: NonEmptyList[CornichonError], initialRunState: RunState) =
    errorsToFailureStep(this, initialRunState.depth, errors)

  override def onSuccess(result: Done, initialRunState: RunState, executionTime: Duration) =
    (successLog(title, initialRunState.depth, show, executionTime), None)
}

trait Assertion { self ⇒
  def validated: ValidatedNel[CornichonError, Done]

  def and(other: Assertion): Assertion = new Assertion {
    def validated = self.validated *> other.validated
  }

  def andAll(others: Seq[Assertion]): Assertion = new Assertion {
    def validated = others.fold(self)(_ and _).validated
  }

  def or(other: Assertion): Assertion = new Assertion {
    def validated =
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

  def all(assertions: List[Assertion]): Assertion = assertions.reduce((acc, assertion) ⇒ acc.and(assertion))
  def any(assertions: List[Assertion]): Assertion = assertions.reduce((acc, assertion) ⇒ acc.or(assertion))
}