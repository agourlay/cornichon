package com.github.agourlay.cornichon.core

import scala.concurrent.duration.Duration

case class FeatureDef(name: String, scenarios: Vector[Scenario]) {
  require(
    scenarios.map(s ⇒ s.name).distinct.length == scenarios.length,
    s"Scenarios name must be unique within a Feature - error caused by duplicated declaration of scenarios '${scenarios.map(s ⇒ s.name).diff(scenarios.map(s ⇒ s.name).distinct).mkString(", ")}'"
  )
}

case class Scenario(name: String, steps: Vector[Step], ignored: Boolean = false)

sealed trait StepAssertion[A] {
  val isSuccess: Boolean
}

case class SimpleStepAssertion[A](expected: A, result: A) extends StepAssertion[A] {
  val isSuccess = expected == result
}

case class DetailedStepAssertion[A](expected: A, result: A, details: A ⇒ String) extends StepAssertion[A] {
  val isSuccess = expected == result
}

sealed trait Step {
  val title: String
}

case class EffectStep(
  title: String,
  effect: Session ⇒ Session,
  show: Boolean = true
) extends Step

case class AssertStep[A](
  title: String,
  action: Session ⇒ StepAssertion[A],
  negate: Boolean = false,
  show: Boolean = true
) extends Step

case class DebugStep(message: Session ⇒ String) extends Step {
  val title = s"Debug step with message `$message`"
}

trait WrapperStep

sealed trait EventuallyStep extends Step with WrapperStep

case class EventuallyStart(conf: EventuallyConf) extends EventuallyStep {
  val title = s"Eventually block with maxDuration = ${conf.maxTime} and interval = ${conf.interval}"
}

case class EventuallyStop(conf: EventuallyConf) extends EventuallyStep {
  val title = s"Eventually closing block with maxDuration = ${conf.maxTime} and interval = ${conf.interval}"
}

case class EventuallyConf(maxTime: Duration, interval: Duration) {
  def consume(burnt: Duration) = {
    val rest = maxTime - burnt
    val newMax = if (rest.lteq(Duration.Zero)) Duration.Zero else rest
    copy(maxTime = newMax)
  }
}

object EventuallyConf {
  def empty = EventuallyConf(Duration.Zero, Duration.Zero)
}

sealed trait ConcurrentStep extends Step with WrapperStep

case class ConcurrentStart(factor: Int, maxTime: Duration) extends ConcurrentStep {
  val title = s"Concurrently block with factor '$factor' and maxTime '$maxTime'"
}

case class ConcurrentStop(factor: Int) extends ConcurrentStep {
  val title = s"Concurrently closing block with factor '$factor'"
}

sealed trait WithinStep extends Step with WrapperStep

case class WithinStart(maxDuration: Duration) extends ConcurrentStep {
  val title = s"Within block with max duration '$maxDuration'"
}

case class WithinStop(maxDuration: Duration) extends ConcurrentStep {
  val title = s"Within closing block with max duration '$maxDuration'"
}