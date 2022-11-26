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

  property("replace_all replaces values in a String") =
    forAll(Gen.alphaStr, Gen.alphaStr) { (ph, value) =>
      val content = s"This project is <$ph> and <$ph>"
      val result = StringUtils.replace_all(content, s"<$ph>", value)
      result == s"This project is $value and $value"
    }
}
