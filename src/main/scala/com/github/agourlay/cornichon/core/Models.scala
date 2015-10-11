package com.github.agourlay.cornichon.core

import scala.concurrent.duration.Duration

case class FeatureDef(name: String, scenarios: Seq[Scenario])

case class Scenario(name: String, steps: Seq[Step], ignored: Boolean = false)

trait Step

case class ExecutableStep[A](
  title: String,
  action: Session ⇒ (A, Session, A),
  negate: Boolean = false,
  show: Boolean = true
) extends Step

object ExecutableStep {
  def effectStep(title: String, effect: Session ⇒ Session, negate: Boolean = false, show: Boolean = true) =
    ExecutableStep[Boolean](
      title = title,
      action = s ⇒ (true, effect(s), true),
      negate = negate,
      show = show
    )
}

case class EventuallyConf(maxTime: Duration, interval: Duration) {
  def consume(duration: Duration) = copy(maxTime = maxTime - duration)
}

object EventuallyConf {
  def empty = EventuallyConf(Duration.Zero, Duration.Zero)
}

trait EventuallyStep extends Step

case class EventuallyStart(conf: EventuallyConf) extends EventuallyStep

case class EventuallyStop(conf: EventuallyConf) extends EventuallyStep

