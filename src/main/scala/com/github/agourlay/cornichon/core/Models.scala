package com.github.agourlay.cornichon.core

import scala.concurrent.duration.Duration

case class FeatureDef(name: String, scenarios: Seq[Scenario])

case class Scenario(name: String, steps: Seq[Step], ignored: Boolean = false)

sealed trait StepAssertion[A] {
  val isSuccess: Boolean
}

case class SimpleStepAssertion[A](expected: A, result: A) extends StepAssertion[A] {
  val isSuccess = expected == result
}

case class DetailedStepAssertion[A](expected: A, result: A, details: A ⇒ String) extends StepAssertion[A] {
  val isSuccess = expected == result
}

object StepAssertion {
  def alwaysOK = SimpleStepAssertion(true, true)
  def neverOK = SimpleStepAssertion(true, false)
}

sealed trait Step {
  val title: String
}

case class ExecutableStep[A](
  title: String,
  action: Session ⇒ (Session, StepAssertion[A]),
  negate: Boolean = false,
  show: Boolean = true
) extends Step

object ExecutableStep {
  def effectStep(title: String, effect: Session ⇒ Session, negate: Boolean = false, show: Boolean = true) =
    ExecutableStep[Boolean](
      title = title,
      action = s ⇒ (effect(s), StepAssertion.alwaysOK),
      negate = negate,
      show = show
    )
}

case class DebugStep(message: Session ⇒ String) extends Step {
  val title = s"Debug step with message `$message`"
}

sealed trait EventuallyStep extends Step

case class EventuallyStart(conf: EventuallyConf) extends EventuallyStep {
  val title = s"Eventually bloc with maxDuration = ${conf.maxTime} and interval = ${conf.interval}"
}

case class EventuallyStop(conf: EventuallyConf) extends EventuallyStep {
  val title = s"Eventually closing bloc with maxDuration = ${conf.maxTime} and interval = ${conf.interval}"
}

case class EventuallyConf(maxTime: Duration, interval: Duration) {
  def consume(duration: Duration) = {
    val rest = maxTime - duration
    val newMax = if (rest.lteq(Duration.Zero)) Duration.Zero else rest
    copy(maxTime = newMax)
  }
}

object EventuallyConf {
  def empty = EventuallyConf(Duration.Zero, Duration.Zero)
}

sealed trait ConcurrentStep extends Step

case class ConcurrentStart(factor: Int, maxTime: Duration) extends ConcurrentStep {
  val title = s"Concurrently bloc with factor '$factor'"
}

case class ConcurrentStop(factor: Int) extends ConcurrentStep {
  val title = s"Concurrently closing bloc with factor '$factor'"
}