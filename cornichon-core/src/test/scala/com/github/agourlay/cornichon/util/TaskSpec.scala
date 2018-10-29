package com.github.agourlay.cornichon.util

import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.Future

trait TaskSpec {

  implicit def taskToFuture[A](t: Task[A])(implicit s: Scheduler): Future[A] =
    t.runToFuture(s)

}
