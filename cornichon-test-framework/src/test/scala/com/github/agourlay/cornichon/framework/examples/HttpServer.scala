package com.github.agourlay.cornichon.framework.examples

import monix.eval.Task
import monix.execution.{ CancelableFuture, Scheduler }

class HttpServer(signal: Task[Unit])(implicit s: Scheduler) {
  def shutdown(): CancelableFuture[Unit] = signal.runToFuture
}