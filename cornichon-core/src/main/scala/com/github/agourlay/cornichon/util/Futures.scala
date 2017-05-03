package com.github.agourlay.cornichon.util

import monix.execution.Scheduler

import scala.concurrent.{ Future, Promise }
import scala.concurrent.duration.FiniteDuration

object Futures {

  def evalAfter[A](waitFor: FiniteDuration)(what: ⇒ Future[A])(implicit scheduler: Scheduler): Future[A] = {
    val promise = Promise[A]()
    scheduler.scheduleOnce(waitFor)(promise.completeWith(what))
    promise.future
  }

  def failAfter[A](after: FiniteDuration)(what: ⇒ Future[A])(error: Exception)(implicit scheduler: Scheduler): Future[A] = {
    val timeoutValue = evalAfter(after)(Future.failed(error))
    Future.firstCompletedOf(Seq(timeoutValue, what))
  }

}