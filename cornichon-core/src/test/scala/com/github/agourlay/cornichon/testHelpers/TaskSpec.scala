package com.github.agourlay.cornichon.testHelpers

import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.Future
import scala.concurrent.duration.Duration

trait TaskSpec {
  implicit val scheduler: Scheduler = Scheduler.Implicits.global
  implicit def taskToFuture[A](t: Task[A]): Future[A] = t.runToFuture(scheduler)
  def awaitTask[A](t: Task[A]): A = t.runSyncUnsafe(Duration.Inf)
}
