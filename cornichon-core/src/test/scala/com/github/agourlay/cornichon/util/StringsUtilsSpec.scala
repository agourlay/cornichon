package com.github.agourlay.cornichon.util

import com.github.agourlay.cornichon.util.StringUtils.printArrowPairs
import munit.FunSuite

class StringsUtilsSpec extends FunSuite {

  test("printArrowPairs with multiple items") {
    val res = printArrowPairs(Seq("a" -> "1", "b" -> "2", "c" -> "3"))
    val expected = "'a' -> '1', 'b' -> '2', 'c' -> '3'"
    assertEquals(res, expected)
  }

  test("printArrowPairs with single item") {
    val res = printArrowPairs(Seq("key" -> "value"))
    assertEquals(res, "'key' -> 'value'")
  }

  test("printArrowPairs with empty sequence") {
    val res = printArrowPairs(Seq.empty)
    assertEquals(res, "")
  }

}
