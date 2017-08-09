package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.scalatest.{ ValidatedMatchers, ValidatedValues }
import org.scalatest.{ Matchers, WordSpec }

class StringAssertionSpec extends WordSpec
  with Matchers
  with ValidatedMatchers
  with ValidatedValues {

  "StringAssertion" must {

    "StringContainsAssertion" must {
      "valid assertion" in {
        StringContainsAssertion("the text string", "text").validated should be(valid)
      }

      "invalid" in {
        StringContainsAssertion("the text string", "other").validated should be(invalid)
      }

    }

    "RegexAssertion" must {
      "valid assertion" in {
        RegexAssertion("the text string sample 434", "\\d+".r).validated should be(valid)
      }

      "invalid" in {
        RegexAssertion("the text string sample 434", "[A-Z]+".r).validated should be(invalid)
      }
    }

  }

}
