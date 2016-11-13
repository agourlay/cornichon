package com.github.agourlay.cornichon.steps.regular.assertStep

import java.util.Timer

import cats.data.Validated._
import cats.data._
import cats.syntax.cartesian._
import cats.syntax.either._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.util.Timing

import scala.concurrent.{ ExecutionContext, Future }

case class AssertStep(title: String, action: Session ⇒ Assertion, show: Boolean = true) extends Step {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext, timer: Timer) = {
    val session = initialRunState.session
    val (res, duration) = Timing.withDuration {
      Either.catchNonFatal(action(session))
        .leftMap(CornichonError.fromThrowable)
        .flatMap(runStepPredicate)
    }
    Future.successful(xorToStepReport(this, res.map(done ⇒ session), initialRunState, show, Some(duration)))
  }

  //TODO propage all errors
  def runStepPredicate(assertion: Assertion): Either[CornichonError, Done] = assertion.validated.toEither.leftMap(_.head)
}

trait Assertion { self ⇒
  def validated: ValidatedNel[CornichonError, Done]

  def and(other: Assertion): Assertion = new Assertion {
    def validated = self.validated *> other.validated
  }

  def or(other: Assertion): Assertion = new Assertion {
    def validated =
      if (self.validated.isValid || other.validated.isValid)
        valid(Done)
      else
        self.validated *> other.validated
  }
}