package com.github.agourlay.cornichon.steps.check.checkModel

import com.github.agourlay.cornichon.core.RandomContext
import munit.FunSuite

class CheckModelEngineSpec extends FunSuite {

  test("pickTransitionAccordingToProbability distributes fairly with two equal weights") {
    // Two transitions with equal 50/50 weights
    val inputs = List((50, "A", true), (50, "B", true))
    val iterations = 10_000
    var countA = 0
    var countB = 0

    for (seed <- 0 until iterations) {
      val rc = RandomContext.fromSeed(seed.toLong)
      val picked = CheckModelEngine.pickTransitionAccordingToProbability(rc, inputs)
      if (picked == "A") countA += 1
      else countB += 1
    }

    // With fair distribution, each should be close to 50%
    // Allow 5% tolerance
    val ratioA = countA.toDouble / iterations
    assert(ratioA > 0.45 && ratioA < 0.55, s"Expected ~50% for A but got ${ratioA * 100}% (countA=$countA, countB=$countB)")
  }

  test("pickTransitionAccordingToProbability respects unequal weights") {
    // 80/20 split
    val inputs = List((80, "A", true), (20, "B", true))
    val iterations = 10_000
    var countA = 0
    var countB = 0

    for (seed <- 0 until iterations) {
      val rc = RandomContext.fromSeed(seed.toLong)
      val picked = CheckModelEngine.pickTransitionAccordingToProbability(rc, inputs)
      if (picked == "A") countA += 1
      else countB += 1
    }

    val ratioA = countA.toDouble / iterations
    assert(ratioA > 0.75 && ratioA < 0.85, s"Expected ~80% for A but got ${ratioA * 100}% (countA=$countA, countB=$countB)")
  }

  test("pickTransitionAccordingToProbability selects only option with weight 100") {
    val inputs = List((100, "A", true))
    val rc = RandomContext.fromSeed(42L)
    val picked = CheckModelEngine.pickTransitionAccordingToProbability(rc, inputs)
    assertEquals(picked, "A")
  }

}
