package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.syntax.validated._

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.core.Done._

import scala.util.matching.Regex

abstract class StringAssertion extends Assertion

case class StringContainsAssertion(input: String, expectedPart: String) extends StringAssertion {
  val validated = if (input.contains(expectedPart)) validDone else StringContainsAssertionError(input, expectedPart).invalidNel
}

case class StringContainsAssertionError(input: String, expectedPart: String) extends CornichonError {
  val baseErrorMessage = s"""expected string '$expectedPart' to be contained but it is not the case with value :
                |$input""".stripMargin
}

case class RegexAssertion(input: String, expectedRegex: Regex) extends StringAssertion {
  val validated = {
    val matching = expectedRegex.findFirstIn(input)
    if (matching.isDefined) validDone else RegexAssertionError(input, expectedRegex).invalidNel
  }
}

case class RegexAssertionError(input: String, expectedRegex: Regex) extends CornichonError {
  val baseErrorMessage = s"""expected regular expression '$expectedRegex' to be matched but it is not the case with value :
                  |$input""".stripMargin
}
