package com.github.agourlay.cornichon.util

import com.github.agourlay.cornichon.util.StringUtils.printArrowPairs
import munit.FunSuite

class StringsUtilsSpec extends FunSuite {

  test("printArrowPairs") {
    val res = printArrowPairs(Seq("a" -> "1", "b" -> "2", "c" -> "3"))
    val expected = "'a' -> '1', 'b' -> '2', 'c' -> '3'"
    assertEquals(res, expected)
  }

}
