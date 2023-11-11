package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.{ Show, Order }
import cats.syntax.show._
import cats.syntax.validated._

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.core.Done._

abstract class OrderAssertion[A] extends Assertion

case class LessThanAssertion[A: Show: Order](left: A, right: A) extends OrderAssertion[A] {
  lazy val validated = if (Order[A].lt(left, right)) validDone else LessThanAssertionError(left, right).invalidNel
}

case class LessThanAssertionError[A: Show](left: A, right: A) extends CornichonError {
  lazy val baseErrorMessage = s"expected '${left.show}' to be less than '${right.show}'"
}

case class GreaterThanAssertion[A: Show: Order](left: A, right: A) extends OrderAssertion[A] {
  lazy val validated = if (Order[A].gt(left, right)) validDone else GreaterThanAssertionError(left, right).invalidNel
}

case class GreaterThanAssertionError[A: Show](left: A, right: A) extends CornichonError {
  lazy val baseErrorMessage = s"expected '${left.show}' to be greater than '${right.show}'"
}

case class BetweenAssertion[A: Show: Order](low: A, inspected: A, high: A) extends OrderAssertion[A] {
  lazy val validated = LessThanAssertion(inspected, high).and(GreaterThanAssertion(inspected, low)).validated
}
