package com.github.agourlay.cornichon.util

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.{ Duration, FiniteDuration }

object Timing {

  def withDuration[A](fct: ⇒ A): (A, Duration) = {
    val now = System.nanoTime
    val res = fct
    val executionTime = Duration.fromNanos(System.nanoTime - now)
    (res, executionTime)
  }

  def withDuration[A](fct: ⇒ Future[A])(implicit ec: ExecutionContext): Future[(A, FiniteDuration)] = {
    val now = System.nanoTime
    fct.map { res ⇒
      val executionTime = Duration.fromNanos(System.nanoTime - now)
      (res, executionTime)
    }
  }

}
