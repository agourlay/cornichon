package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.{ Order, Show }
import cats.syntax.show._
import com.github.agourlay.cornichon.core.{ CornichonError, ScenarioContext }

abstract class GenericAssertStepBuilder[A: Show: Order: Diff] {

  protected val baseTitle: String
  protected def sessionExtractor(sc: ScenarioContext): Either[CornichonError, (A, Option[() => String])]

  def is(expected: A): AssertStep = {
    val fullTitle = s"$baseTitle is '$expected'"
    AssertStep(
      title = fullTitle,
      action = s => Assertion.either {
        sessionExtractor(s).map {
          case (asserted, source) =>
            source match {
              case None =>
                GenericEqualityAssertion(asserted, expected)
              case Some(info) =>
                CustomMessageEqualityAssertion(asserted, expected, () => s"'${asserted.show}' was not equal to '${expected.show}' for context\n${info()}")
            }
        }
      }
    )
  }

  def isLessThan(lessThan: A): AssertStep = {
    val fullTitle = s"$baseTitle is less than '$lessThan'"
    AssertStep(
      title = fullTitle,
      action = s => Assertion.either {
        sessionExtractor(s).map { case (asserted, _) => LessThanAssertion(asserted, lessThan) }
      }
    )
  }

  def isGreaterThan(greaterThan: A): AssertStep = {
    val fullTitle = s"$baseTitle is greater than '$greaterThan'"
    AssertStep(
      title = fullTitle,
      action = s => Assertion.either {
        sessionExtractor(s).map { case (asserted, _) => GreaterThanAssertion(asserted, greaterThan) }
      }
    )
  }

  def isBetween(less: A, greater: A): AssertStep = {
    val fullTitle = s"$baseTitle is between '$less' and '$greater'"
    AssertStep(
      title = fullTitle,
      action = s => Assertion.either {
        sessionExtractor(s).map { case (asserted, _) => BetweenAssertion(less, asserted, greater) }
      }
    )
  }
}
