package com.github.agourlay.cornichon.steps.regular.assertStep

import java.util.Timer

import cats.{ Eq, Order, Show }
import cats.data.{ ValidatedNel, Xor }
import cats.data.Validated._
import cats.syntax.show._
import cats.syntax.cartesian._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.util.Timing

import scala.concurrent.{ ExecutionContext, Future }

case class AssertStep(title: String, action: Session ⇒ Assertion, show: Boolean = true) extends Step {

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

  //TODO propage all errors
  def runStepPredicate(assertion: Assertion): Xor[CornichonError, Done] = assertion.validated.toXor.leftMap(_.head)
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

abstract class OrderAssertion[A: Show: Order] extends Assertion

case class LessThanAssertion[A: Show: Order](left: A, right: A) extends OrderAssertion[A] {
  val validated: ValidatedNel[CornichonError, Done] =
    if (Order[A].lt(left, right)) valid(Done) else invalidNel(LessThanAssertionError(left, right))
}

case class LessThanAssertionError[A: Show](left: A, right: A) extends CornichonError {
  val msg = s"expected '${left.show}' to be less than '${right.show}'"
}

case class GreaterThanAssertion[A: Show: Order](left: A, right: A) extends OrderAssertion[A] {
  val validated: ValidatedNel[CornichonError, Done] =
    if (Order[A].gt(left, right)) valid(Done) else invalidNel(GreaterThanAssertionError(left, right))
}

case class GreaterThanAssertionError[A: Show](left: A, right: A) extends CornichonError {
  val msg = s"expected '${left.show}' to be greater than '${right.show}'"
}

case class BetweenAssertion[A: Show: Order](low: A, inspected: A, high: A) extends OrderAssertion[A] {
  val validated = LessThanAssertion(inspected, high).and(GreaterThanAssertion(inspected, low)).validated
}

abstract class EqualityAssertion[A: Eq] extends Assertion {
  val expected: A
  val actual: A

  val negate: Boolean
  val assertionError: CornichonError
  val expectedEqualsActual = Eq[A].eqv(expected, actual)

  val validated = {
    val succeedAsExpected = expectedEqualsActual && !negate
    val failedAsExpected = !expectedEqualsActual && negate

    if (succeedAsExpected || failedAsExpected)
      valid(Done)
    else
      invalidNel(assertionError)
  }
}

case class GenericEqualityAssertion[A: Show: Diff: Eq](expected: A, actual: A, negate: Boolean = false) extends EqualityAssertion[A] {
  lazy val assertionError = GenericEqualityAssertionError(expected, actual, negate)
}

case class GenericEqualityAssertionError[A: Show: Diff](expected: A, actual: A, negate: Boolean) extends CornichonError {
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
