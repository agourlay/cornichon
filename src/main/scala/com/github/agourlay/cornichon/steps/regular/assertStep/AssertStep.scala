package com.github.agourlay.cornichon.steps.regular.assertStep

import java.util.Timer

import cats.{ Eq, Show }
import cats.data.Xor
import cats.data.Xor._
import cats.syntax.show._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.util.Timing

import scala.concurrent.{ ExecutionContext, Future }

case class AssertStep[A](title: String, action: Session ⇒ Assertion[A], show: Boolean = true) extends Step {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext, timer: Timer) = {
    val session = initialRunState.session
    val (res, duration) = Timing.withDuration {
      Xor.catchNonFatal(action(session))
        .leftMap(CornichonError.fromThrowable)
        .flatMap(runStepPredicate(session))
    }
    Future.successful(xorToStepReport(this, res, initialRunState, show, Some(duration)))
  }

  def runStepPredicate(newSession: Session)(assertion: Assertion[A]): Xor[CornichonError, Session] =
    if (assertion.isSuccessful)
      right(newSession)
    else
      left(assertion.assertionError)

}

abstract class Assertion[A: Eq] {
  val expected: A
  val actual: A
  val expectedEqualsActual = Eq[A].eqv(expected, actual)
  val isSuccessful = {
    val succeedAsExpected = expectedEqualsActual && !negate
    val failedAsExpected = !expectedEqualsActual && negate

    succeedAsExpected || failedAsExpected
  }
  val negate: Boolean
  def assertionError: CornichonError
}

case class GenericAssertion[A: Show: Diff: Eq](expected: A, actual: A, negate: Boolean = false) extends Assertion[A] {
  lazy val assertionError = GenericAssertionError(expected, actual, negate)
}

case class GenericAssertionError[A: Show: Diff](expected: A, actual: A, negate: Boolean) extends CornichonError {
  private val baseMsg =
    s"""|expected result was${if (negate) " different than:" else ":"}
        |'${expected.show}'
        |but actual result is:
        |'${actual.show}'
        |""".stripMargin.trim

  val msg = Diff[A].diff(expected, actual).fold(baseMsg) { diffMsg ⇒
    s"""|$baseMsg
        |
        |$diffMsg
      """.stripMargin.trim
  }
}

case class CustomMessageAssertion[A: Eq](expected: A, actual: A, customMessage: A ⇒ String, negate: Boolean = false) extends Assertion[A] {
  lazy val assertionError = CustomMessageAssertionError(actual, customMessage)
}

case class CustomMessageAssertionError[A](result: A, detailedAssertion: A ⇒ String) extends CornichonError {
  val msg = detailedAssertion(result)
}
