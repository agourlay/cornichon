package com.github.agourlay.cornichon.steps.regular.assertStep

import org.scalatest.{ Matchers, WordSpec }

class StringAssertionSpec extends WordSpec with Matchers {

  "StringAssertion" must {

    "StringContainsAssertion" must {
      "valid assertion" in {
        StringContainsAssertion("the text string", "text").validated.isValid should be(true)
      }

      "invalid" in {
        StringContainsAssertion("the text string", "other").validated.isValid should be(false)
      }

    }

    "RegexAssertion" must {
      "valid assertion" in {
        RegexAssertion("the text string sample 434", "\\d+".r).validated.isValid should be(true)
      }

      "invalid" in {
        RegexAssertion("the text string sample 434", "[A-Z]+".r).validated.isValid should be(false)
      }
    }

  }

}
