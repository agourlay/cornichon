package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.{ Show, Order }
import cats.syntax.show._
import cats.data.Validated._
import cats.data._
import com.github.agourlay.cornichon.core.{ CornichonError, Done }

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
