package com.github.agourlay.cornichon.core

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong
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
  def uniqueLong(): Long
  def nextString(length: Int): String
  def nextPrintableChar(): Char
  def alphanumeric(length: Int): String
  def shuffle[T](xs: Iterable[T]): Iterable[T]
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
  def alphanumeric(length: Int): String = RandomContext.mkAlphaNumStr(seededRandom, length)
  def shuffle[T](xs: Iterable[T]): Iterable[T] = seededRandom.shuffle(xs)
  private val atomicLong = new AtomicLong(1L)
  def uniqueLong(): Long = atomicLong.getAndIncrement()
}

object RandomContext {
  def fromOptSeed(withSeed: Option[Long]): RandomContext = {
    val initialSeed = withSeed.getOrElse(System.currentTimeMillis())
    fromSeed(initialSeed)
  }

  def fromSeed(seed: Long): RandomContext = {
    new MutableRandomContext(seed, new Random(new java.util.Random(seed)))
  }

  private val alphanumeric = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".getBytes

  // Faster than Randon.alphanumeric
  protected[cornichon] def mkAlphaNumStr(rand: Random, length: Int): String = {
    val bytes = new Array[Byte](length)
    for (i <- 0 until length) bytes(i) = alphanumeric(rand.nextInt(alphanumeric.length))
    new String(bytes, StandardCharsets.US_ASCII)
  }
}