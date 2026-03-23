package com.github.agourlay.cornichon.util

import org.scalacheck.{Gen, Properties}
import org.scalacheck.Prop._

class StringsUtilsProperties extends Properties("StringsUtil") {

  property("levenshtein compute distance zero for identical String") = forAll(Gen.alphaStr.filter(_.trim.nonEmpty)) { s =>
    StringUtils.levenshtein(s, s) == 0
  }

  property("compute distance one for String with one addition") = forAll(Gen.alphaStr.filter(_.trim.nonEmpty)) { s =>
    StringUtils.levenshtein(s, s + "a") == 1
  }

  property("compute distance one for String with one deletion") = forAll(Gen.alphaStr.filter(_.trim.nonEmpty)) { s =>
    StringUtils.levenshtein(s, s.tail) == 1
  }

  property("levenshtein is symmetric") = forAll(Gen.alphaStr, Gen.alphaStr) { (a, b) =>
    StringUtils.levenshtein(a, b) == StringUtils.levenshtein(b, a)
  }

  property("levenshtein satisfies triangle inequality") = forAll(Gen.alphaStr, Gen.alphaStr, Gen.alphaStr) { (a, b, c) =>
    StringUtils.levenshtein(a, c) <= StringUtils.levenshtein(a, b) + StringUtils.levenshtein(b, c)
  }

  property("levenshtein distance from empty string is string length") = forAll(Gen.alphaStr) { s =>
    StringUtils.levenshtein("", s) == s.length
  }

  property("levenshtein distance to empty string is string length") = forAll(Gen.alphaStr) { s =>
    StringUtils.levenshtein(s, "") == s.length
  }

  property("levenshtein distance between empty strings is zero") = StringUtils.levenshtein("", "") == 0

  property("levenshtein is non-negative") = forAll(Gen.alphaStr, Gen.alphaStr) { (a, b) =>
    StringUtils.levenshtein(a, b) >= 0
  }

  property("replace_patterns_in_order replaces values in a String") = forAll(Gen.alphaStr.filter(_.trim.nonEmpty), Gen.alphaStr.filter(_.trim.nonEmpty)) { (ph, value) =>
    val content = s"This project is <$ph> and <$ph>"
    val result = StringUtils.replacePatternsInOrder(content, Vector((s"<$ph>", value), (s"<$ph>", value)))
    result == s"This project is $value and $value"
  }

}
