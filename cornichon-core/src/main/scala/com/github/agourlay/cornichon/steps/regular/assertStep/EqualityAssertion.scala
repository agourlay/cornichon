package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.{ Eq, Show }
import cats.syntax.show._
import cats.syntax.validated._

import com.github.agourlay.cornichon.core.{ CornichonError, Session, SessionKey }
import com.github.agourlay.cornichon.core.Done._

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
      validDone
    else
      assertionError.invalidNel
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

  val baseErrorMessage = Diff[A].diff(expected, actual).fold(baseMsg) { diffMsg ⇒
    s"""|$baseMsg
        |
        |$diffMsg
      """.stripMargin.trim
  }
}

case class CustomMessageEqualityAssertion[A: Eq](expected: A, actual: A, customMessage: A ⇒ String, negate: Boolean = false) extends EqualityAssertion[A] {
  lazy val assertionError = CustomMessageAssertionError(actual, customMessage)
}

object CustomMessageEqualityAssertion {
  def fromSession[A: Eq](s: Session, key: SessionKey)(transformSessionValue: (Session, String) ⇒ Either[CornichonError, (A, A, A ⇒ String)]) =
    Assertion.either {
      s.get(key)
        .flatMap(transformSessionValue(s, _))
        .map { r ⇒
          val (expected, actual, details) = r
          CustomMessageEqualityAssertion(expected, actual, details)
        }
    }

}

case class CustomMessageAssertionError[A](result: A, detailedAssertion: A ⇒ String) extends CornichonError {
  val baseErrorMessage = detailedAssertion(result)
}
