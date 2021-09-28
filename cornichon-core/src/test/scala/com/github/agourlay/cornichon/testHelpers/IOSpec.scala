package com.github.agourlay.cornichon.testHelpers

import cats.effect.IO
import cats.effect.unsafe.IORuntime

import scala.concurrent.Future

trait IOSpec {
  implicit val scheduler: IORuntime = cats.effect.unsafe.implicits.global
  implicit def ioToFuture[A](t: IO[A]): Future[A] = t.unsafeToFuture()
  def awaitIO[A](t: IO[A]): A = t.unsafeRunSync()
}
