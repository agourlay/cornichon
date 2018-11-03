package com.github.agourlay.cornichon.check.examples

import fs2.concurrent.SignallingRef
import monix.eval.Task
import monix.execution.{ CancelableFuture, Scheduler }

class HttpServer(signal: SignallingRef[Task, Boolean])(implicit s: Scheduler) {
  def shutdown(): CancelableFuture[Unit] = signal.set(true).runToFuture
}