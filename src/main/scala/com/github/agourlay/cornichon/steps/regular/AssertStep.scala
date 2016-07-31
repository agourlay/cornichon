package com.github.agourlay.cornichon.steps.regular

import cats.data.Xor
import cats.data.Xor._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._

import scala.concurrent.ExecutionContext

case class AssertStep[A](
    title: String,
    action: Session ⇒ StepAssertion[A],
    negate: Boolean = false,
    show: Boolean = true
) extends Step {

  override def run(engine: Engine, initialRunState: RunState)(implicit ec: ExecutionContext) = {
    val session = initialRunState.session
    val res = Xor.catchNonFatal(action(session))
      .leftMap(CornichonError.fromThrowable)
      .flatMap { assertion ⇒
        runStepPredicate(session, assertion)
      }
    xorToStepReport(this, res, title, initialRunState, show)
  }

  //TODO think about making StepAssertion concrete implem. custom as well
  def runStepPredicate(newSession: Session, stepAssertion: StepAssertion[A]): Xor[CornichonError, Session] = {
    val succeedAsExpected = stepAssertion.isSuccess && !negate
    val failedAsExpected = !stepAssertion.isSuccess && negate

    if (succeedAsExpected || failedAsExpected) right(newSession)
    else
      stepAssertion match {
        case SimpleStepAssertion(expected, actual) ⇒
          left(StepAssertionError(expected, actual, negate))
        case DetailedStepAssertion(expected, actual, details) ⇒
          left(DetailedStepAssertionError(actual, details))
      }
  }
}
