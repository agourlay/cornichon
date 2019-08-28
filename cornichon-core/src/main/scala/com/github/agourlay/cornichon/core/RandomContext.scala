package com.github.agourlay.cornichon.core

import scala.util.Random

// FIXME seededRandom works through internal mutation :(
// use purely functional number generation instead
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