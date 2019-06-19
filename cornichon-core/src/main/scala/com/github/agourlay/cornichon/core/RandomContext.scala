package com.github.agourlay.cornichon.core

import scala.util.Random

// seededRandom works through internal mutation :(
case class RandomContext(initialSeed: Long, seededRandom: Random)

object RandomContext {
  def fromOptSeed(withSeed: Option[Long]): RandomContext = {
    val initialSeed = withSeed.getOrElse(System.currentTimeMillis())
    fromSeed(initialSeed)
  }

  def fromSeed(seed: Long): RandomContext = {
    RandomContext(seed, new Random(new java.util.Random(seed)))
  }
}