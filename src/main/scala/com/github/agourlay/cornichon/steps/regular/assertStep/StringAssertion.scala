package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.data.Validated._
import cats.data._
import com.github.agourlay.cornichon.core.{ CornichonError, Done }

abstract class StringAssertion extends Assertion

case class StringContainsAssertion(input: String, expectedPart: String) extends Assertion {
  val validated: ValidatedNel[CornichonError, Done] =
    if (input.contains(expectedPart)) valid(Done) else invalidNel(StringContainsAssertionError(input, expectedPart))
}

case class StringContainsAssertionError(input: String, expectedPart: String) extends CornichonError {
  val baseErrorMessage = s"""expected string '$expectedPart' to be contained but it is not the case with value :
                |$input""".stripMargin
}
