package com.github.agourlay.cornichon.util

import org.scalacheck.{ Gen, Properties }
import org.scalacheck.Prop._

class StringsUtilsProperties extends Properties("StringsUtil") {

  property("levenshtein compute distance zero for identical String") =
    forAll(Gen.alphaStr.filter(_.trim.nonEmpty)) { s =>
      StringUtils.levenshtein(s, s) == 0
    }

  property("compute distance one for String with one addition") =
    forAll(Gen.alphaStr.filter(_.trim.nonEmpty)) { s =>
      StringUtils.levenshtein(s, s + "a") == 1
    }

  property("compute distance one for String with one deletion") =
    forAll(Gen.alphaStr.filter(_.trim.nonEmpty)) { s =>
      StringUtils.levenshtein(s, s.tail) == 1
    }
}
