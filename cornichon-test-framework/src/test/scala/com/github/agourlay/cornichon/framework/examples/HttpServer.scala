package com.github.agourlay.cornichon.framework.examples

import cats.effect.IO
import cats.effect.unsafe.IORuntime

import scala.concurrent.Future

class HttpServer(signal: IO[Unit])(implicit s: IORuntime) {
  def shutdown(): Future[Unit] = signal.unsafeToFuture()
}