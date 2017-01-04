package com.github.agourlay.cornichon.util

import java.util.concurrent.ScheduledExecutorService

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration.FiniteDuration

object Timeouts {

  def timeout[A](waitFor: FiniteDuration)(what: ⇒ Future[A])(implicit ec: ExecutionContext, timer: ScheduledExecutorService): Future[A] = {
    val promise = Promise[A]()
    timer.schedule(
      new Runnable() {
        override def run() = Future {
          promise.completeWith(what)
        }
      }, waitFor.length, waitFor.unit
    )

    promise.future
  }

  def failAfter[A](after: FiniteDuration)(what: ⇒ Future[A])(error: Exception)(implicit ec: ExecutionContext, timer: ScheduledExecutorService): Future[A] = {
    val timeoutValue = timeout(after)(Future.failed(error))
    Future.firstCompletedOf(Seq(timeoutValue, what))
  }

}