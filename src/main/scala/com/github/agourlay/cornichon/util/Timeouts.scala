package com.github.agourlay.cornichon.util

import akka.actor.Scheduler

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration.FiniteDuration

object Timeouts {

  def timeout[A](waitFor: FiniteDuration)(what: ⇒ Future[A])(implicit ec: ExecutionContext, scheduler: Scheduler): Future[A] = {
    val promise = Promise[A]()
    scheduler.scheduleOnce(waitFor)(promise.completeWith(what))
    promise.future
  }

  def failAfter[A](after: FiniteDuration)(what: ⇒ Future[A])(error: Exception)(implicit ec: ExecutionContext, scheduler: Scheduler): Future[A] = {
    val timeoutValue = timeout(after)(Future.failed(error))
    Future.firstCompletedOf(Seq(timeoutValue, what))
  }

}