package com.github.agourlay.cornichon.util

import com.github.agourlay.cornichon.util.Printing.printArrowPairs
import munit.FunSuite

class PrintingSpec extends FunSuite {

  test("printArrowPairs") {
    val res = printArrowPairs(Seq("a" -> "1", "b" -> "2", "c" -> "3"))
    val expected = "'a' -> '1', 'b' -> '2', 'c' -> '3'"
    assertEquals(res, expected)
  }

}
