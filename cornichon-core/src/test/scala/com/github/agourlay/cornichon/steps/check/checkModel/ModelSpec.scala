package com.github.agourlay.cornichon.steps.check.checkModel

import munit.FunSuite
import com.github.agourlay.cornichon.core.{NoOpStep, NoValue}

class ModelSpec extends FunSuite {

  private val propA: PropertyN[NoValue, NoValue, NoValue, NoValue, NoValue, NoValue] =
    Property0(description = "A", invariant = () => NoOpStep)
  private val propB: PropertyN[NoValue, NoValue, NoValue, NoValue, NoValue, NoValue] =
    Property0(description = "B", invariant = () => NoOpStep)
  private val propC: PropertyN[NoValue, NoValue, NoValue, NoValue, NoValue, NoValue] =
    Property0(description = "C", invariant = () => NoOpStep)

  test("always creates a single transition with weight 100") {
    val result = Model.always(propA)
    assertEquals(result, List((100, propA)))
  }

  test("weighted creates transitions from varargs") {
    val result = Model.weighted(60 -> propA, 30 -> propB, 10 -> propC)
    assertEquals(result, List((60, propA), (30, propB), (10, propC)))
  }

  test("equallyDistributed splits evenly for 2 properties") {
    val result = Model.equallyDistributed(propA, propB)
    assertEquals(result, List((50, propA), (50, propB)))
  }

  test("equallyDistributed splits evenly for 3 properties with remainder on first") {
    val result = Model.equallyDistributed(propA, propB, propC)
    // 100 / 3 = 33 remainder 1, so first gets 34
    assertEquals(result, List((34, propA), (33, propB), (33, propC)))
  }

  test("equallyDistributed weights sum to 100") {
    for (n <- 1 to 6) {
      val props = (1 to n).map(i => Property0(description = s"P$i", invariant = () => NoOpStep): PropertyN[NoValue, NoValue, NoValue, NoValue, NoValue, NoValue])
      val result = Model.equallyDistributed(props: _*)
      assertEquals(result.map(_._1).sum, 100, s"weights don't sum to 100 for $n properties")
    }
  }

}
