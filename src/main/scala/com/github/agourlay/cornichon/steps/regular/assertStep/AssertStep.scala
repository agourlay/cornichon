package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.Show
import cats.data.Xor
import cats.data.Xor._
import cats.syntax.show._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.core._

import scala.concurrent.ExecutionContext

case class AssertStep[A](title: String, action: Session ⇒ Assertion[A], show: Boolean = true) extends Step {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext) = {
    val session = initialRunState.session
    val res = Xor.catchNonFatal(action(session))
      .leftMap(CornichonError.fromThrowable)
      .flatMap(runStepPredicate(session))
    xorToStepReport(this, res, initialRunState, show)
  }

  def runStepPredicate(newSession: Session)(assertion: Assertion[A]): Xor[CornichonError, Session] =
    if (assertion.isSuccessful)
      right(newSession)
    else
      left(assertion.assertionError)

}

// TODO Introduce Equal type classes
abstract class Assertion[A] {
  val expected: A
  val actual: A
  val expectedEqualsActual = expected == actual
  val isSuccessful = {
    val succeedAsExpected = expectedEqualsActual && !negate
    val failedAsExpected = !expectedEqualsActual && negate

    succeedAsExpected || failedAsExpected
  }
  val negate: Boolean
  def assertionError: CornichonError
}

case class GenericAssertion[A: Show: Diff](expected: A, actual: A, negate: Boolean = false) extends Assertion[A] {
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

case class CustomMessageAssertion[A](expected: A, actual: A, customMessage: A ⇒ String, negate: Boolean = false) extends Assertion[A] {
  lazy val assertionError = CustomMessageAssertionError(actual, customMessage)
}

case class CustomMessageAssertionError[A](result: A, detailedAssertion: A ⇒ String) extends CornichonError {
  val msg = detailedAssertion(result)
}
