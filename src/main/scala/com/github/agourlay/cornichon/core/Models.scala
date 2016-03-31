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

sealed trait WrapperStep extends Step

sealed trait EventuallyStep extends WrapperStep

case class EventuallyStart(conf: EventuallyConf) extends EventuallyStep {
  val title = s"Eventually block with maxDuration = ${conf.maxTime} and interval = ${conf.interval}"
}

case object EventuallyStop extends EventuallyStep {
  val title = s"Eventually block end"
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

sealed trait ConcurrentStep extends WrapperStep

case class ConcurrentStart(factor: Int, maxTime: Duration) extends ConcurrentStep {
  require(factor > 0, "concurrently block must contain a positive factor")
  val title = s"Concurrently block with factor '$factor' and maxTime '$maxTime'"
}

case object ConcurrentStop extends ConcurrentStep {
  val title = s"Concurrently block end"
}

sealed trait WithinStep extends WrapperStep

case class WithinStart(maxDuration: Duration) extends ConcurrentStep {
  val title = s"Within block with max duration '$maxDuration'"
}

case object WithinStop extends ConcurrentStep {
  val title = s"Within block end"
}

sealed trait RepeatStep extends WrapperStep

case class RepeatStart(occurence: Int) extends RepeatStep {
  require(occurence > 0, "repeat block must contain a positive number of occurence")
  val title = s"Repeat block with occurence '$occurence'"
}

case object RepeatStop extends RepeatStep {
  val title = s"Repeat block end"
}

sealed trait RepeatDuringStep extends WrapperStep

case class RepeatDuringStart(duration: Duration) extends RepeatDuringStep {
  val title = s"Repeat block during '$duration'"
}

case object RepeatDuringStop extends RepeatDuringStep {
  val title = s"Repeat block during end"
}