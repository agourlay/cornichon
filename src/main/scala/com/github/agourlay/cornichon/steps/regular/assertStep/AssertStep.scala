package com.github.agourlay.cornichon.steps.regular.assertStep

import java.util.Timer

import cats.{ Eq, Order, Show }
import cats.data.{ Validated, Xor }
import cats.data.Validated._
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
        .flatMap(runStepPredicate)
    }
    Future.successful(xorToStepReport(this, res.map(done ⇒ session), initialRunState, show, Some(duration)))
  }

  def runStepPredicate(assertion: Assertion[A]): Xor[CornichonError, Done] =
    assertion.isValid.toXor
}

trait Assertion[A] {
  val isValid: Validated[CornichonError, Done]
}

abstract class OrderAssertion[A: Show: Order] extends Assertion[A] {
  val right: A
  val left: A

  val isOrdered: Boolean
  val assertionError: CornichonError

  val isValid = if (isOrdered)
    valid(Done)
  else
    invalid(assertionError)
}

case class LessThan[A: Show: Order](left: A, right: A) extends OrderAssertion[A] {
  val isOrdered = Order[A].lt(x = left, y = right)
  lazy val assertionError = LessThanAssertionError(left, right)
}

case class LessThanAssertionError[A: Show](left: A, right: A) extends CornichonError {
  val msg = s"expected '${left.show}' to be less than '${right.show}'"
}

case class GreaterThan[A: Show: Order](left: A, right: A) extends OrderAssertion[A] {
  val isOrdered = Order[A].gt(x = left, y = right)
  lazy val assertionError = GreaterThanAssertionError(left, right)
}

case class GreaterThanAssertionError[A: Show](left: A, right: A) extends CornichonError {
  val msg = s"expected '${left.show}' to be greater than '${right.show}'"
}

abstract class EqualityAssertion[A: Eq] extends Assertion[A] {
  val expected: A
  val actual: A

  val negate: Boolean
  val assertionError: CornichonError
  val expectedEqualsActual = Eq[A].eqv(expected, actual)

  val isValid = {
    val succeedAsExpected = expectedEqualsActual && !negate
    val failedAsExpected = !expectedEqualsActual && negate

    if (succeedAsExpected || failedAsExpected)
      valid(Done)
    else
      invalid(assertionError)
  }
}

case class GenericEqualityAssertion[A: Show: Diff: Eq](expected: A, actual: A, negate: Boolean = false) extends EqualityAssertion[A] {
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

case class CustomMessageEqualityAssertion[A: Eq](expected: A, actual: A, customMessage: A ⇒ String, negate: Boolean = false) extends EqualityAssertion[A] {
  lazy val assertionError = CustomMessageAssertionError(actual, customMessage)
}

case class CustomMessageAssertionError[A](result: A, detailedAssertion: A ⇒ String) extends CornichonError {
  val msg = detailedAssertion(result)
}
