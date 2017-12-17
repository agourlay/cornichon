package com.github.agourlay.cornichon.util

import monix.eval.Task

import scala.concurrent.duration.{ Duration, FiniteDuration }

object Timing {

  def withDuration[A](fct: ⇒ Task[A]): Task[(A, FiniteDuration)] = {
    val now = System.nanoTime
    fct.map { res ⇒
      val executionTime = Duration.fromNanos(System.nanoTime - now)
      (res, executionTime)
    }
  }

}
