package com.github.agourlay.cornichon.util

import org.scalacheck.Gen
import org.scalatest.{ Matchers, WordSpec }
import org.scalatest.prop.PropertyChecks

class StringsSpec extends WordSpec
  with Matchers
  with PropertyChecks {

  "Strings" when {
    "levenshtein" must {
      "compute distance zero for identical String" in {
        forAll(Gen.alphaStr.filter(_.trim.nonEmpty)) { s ⇒
          Strings.levenshtein(s, s) should be(0)
        }
      }

      "compute distance one for String with one addition" in {
        forAll(Gen.alphaStr.filter(_.trim.nonEmpty)) { s ⇒
          Strings.levenshtein(s, s + "a") should be(1)
        }
      }

      "compute distance one for String with one deletion" in {
        forAll(Gen.alphaStr.filter(_.trim.nonEmpty)) { s ⇒
          Strings.levenshtein(s, s.tail) should be(1)
        }
      }
    }
  }
}
