package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.{ Eq, Show }
import cats.syntax.show._
import cats.syntax.validated._

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.core.Done._

abstract class EqualityAssertion[A: Eq] extends Assertion {
  val expected: A
  val actual: A

  val negate: Boolean
  val assertionError: CornichonError

  val validated = {
    val expectedEqualsActual = Eq[A].eqv(expected, actual)
    val succeedAsExpected = expectedEqualsActual && !negate
    val failedAsExpected = !expectedEqualsActual && negate

    if (succeedAsExpected || failedAsExpected)
      validDone
    else
      assertionError.invalidNel
  }
}

case class GenericEqualityAssertion[A: Show: Diff: Eq](expected: A, actual: A, negate: Boolean = false) extends EqualityAssertion[A] {
  lazy val assertionError = GenericEqualityAssertionError(expected, actual, negate)
}

case class GenericEqualityAssertionError[A: Show: Diff](expected: A, actual: A, negate: Boolean) extends CornichonError {
  private lazy val baseMsg =
    s"""|expected result was${if (negate) " different than:" else ":"}
        |'${expected.show}'
        |but actual result is:
        |'${actual.show}'
        |""".stripMargin.trim

  lazy val baseErrorMessage = Diff[A].diff(expected, actual).fold(baseMsg) { diffMsg ⇒
    s"""|$baseMsg
        |
        |$diffMsg
      """.stripMargin.trim
  }
}

case class CustomMessageEqualityAssertion[A: Eq](expected: A, actual: A, customMessage: () ⇒ String, negate: Boolean = false) extends EqualityAssertion[A] {
  lazy val assertionError = CornichonError.fromString(customMessage())
}
