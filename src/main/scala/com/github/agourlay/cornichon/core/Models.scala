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

trait Step

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

case class EventuallyConf(maxTime: Duration, interval: Duration) {
  def consume(duration: Duration) = copy(maxTime = maxTime - duration)
}

object EventuallyConf {
  def empty = EventuallyConf(Duration.Zero, Duration.Zero)
}

trait EventuallyStep extends Step

case class EventuallyStart(conf: EventuallyConf) extends EventuallyStep

case class EventuallyStop(conf: EventuallyConf) extends EventuallyStep

