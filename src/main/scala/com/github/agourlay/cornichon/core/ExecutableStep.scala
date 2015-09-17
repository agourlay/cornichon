package com.github.agourlay.cornichon.core

import scala.concurrent.duration.Duration

trait Step

case class ExecutableStep[A](
  title: String,
  action: Session ⇒ (A, Session),
  expected: A,
  negate: Boolean = false,
  show: Boolean = true
) extends Step

case class EventuallyConf(maxTime: Duration, interval: Duration) {
  def consume(duration: Duration) = copy(maxTime = maxTime - duration)
}

object EventuallyConf {
  def empty = EventuallyConf(Duration.Zero, Duration.Zero)
}

trait EventuallyStep extends Step

case class EventuallyStart(conf: EventuallyConf) extends EventuallyStep

case class EventuallyStop(conf: EventuallyConf) extends EventuallyStep