package com.github.agourlay.cornichon.util

import java.util.{ TimerTask, Timer }

import scala.concurrent.{ Future, Promise, ExecutionContext }
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

// Credits goes to https://github.com/johanandren/futiles/blob/master/src/main/scala/markatta/futiles/Timeouts.scala#L32
// Copied here because needed only a single method from the jar and added Future version
object Timeouts {

  private val timer = new Timer()

  /**
   * When ```waitFor``` has passed, evaluate ```what``` on the given execution context and complete the future
   */
  def timeout[A](waitFor: FiniteDuration)(what: ⇒ A)(implicit ec: ExecutionContext): Future[A] = {
    val promise = Promise[A]()
    timer.schedule(new TimerTask {
      override def run(): Unit = {
        // make sure we do not block the timer thread
        Future {
          promise.complete(Try { what })
        }
      }
    }, waitFor.toMillis)

    promise.future
  }

  /**
   * When ```waitFor``` has passed, evaluate ```what``` on the given execution context and complete the future
   */
  def timeoutF[A](waitFor: FiniteDuration)(what: ⇒ Future[A])(implicit ec: ExecutionContext): Future[A] = {
    val promise = Promise[A]()
    timer.schedule(new TimerTask {
      override def run(): Unit = {
        // make sure we do not block the timer thread
        promise.completeWith(what)
      }
    }, waitFor.toMillis)

    promise.future
  }

}