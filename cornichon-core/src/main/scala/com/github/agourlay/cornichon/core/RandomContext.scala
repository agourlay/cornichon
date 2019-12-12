package com.github.agourlay.cornichon.core

import scala.util.Random

trait RandomContext {
  val initialSeed: Long
  def nextBoolean(): Boolean
  def nextDouble(): Double
  def nextFloat(): Float
  def nextGaussian(): Double
  def nextInt(): Int
  def nextInt(n: Int): Int
  def nextLong(): Long
  def nextString(length: Int): String
  def nextPrintableChar(): Char
  def alphanumeric: Stream[Char]
  def shuffle[T](xs: Traversable[T]): Traversable[T]
}

// FIXME seededRandom works through internal mutation https://github.com/agourlay/cornichon/issues/303
class MutableRandomContext(seed: Long, seededRandom: Random) extends RandomContext {
  val initialSeed: Long = seed
  def nextBoolean(): Boolean = seededRandom.nextBoolean()
  def nextDouble(): Double = seededRandom.nextDouble()
  def nextFloat(): Float = seededRandom.nextFloat()
  def nextGaussian(): Double = seededRandom.nextGaussian()
  def nextInt(): Int = seededRandom.nextInt()
  def nextInt(n: Int): Int = seededRandom.nextInt(n)
  def nextLong(): Long = seededRandom.nextLong()
  def nextString(length: Int): String = seededRandom.nextString(length)
  def nextPrintableChar(): Char = seededRandom.nextPrintableChar()
  def alphanumeric: Stream[Char] = seededRandom.alphanumeric
  def shuffle[T](xs: Traversable[T]): Traversable[T] = seededRandom.shuffle(xs)
}

object RandomContext {
  def fromOptSeed(withSeed: Option[Long]): RandomContext = {
    val initialSeed = withSeed.getOrElse(System.currentTimeMillis())
    fromSeed(initialSeed)
  }

  def fromSeed(seed: Long): RandomContext = {
    new MutableRandomContext(seed, new Random(new java.util.Random(seed)))
  }
}