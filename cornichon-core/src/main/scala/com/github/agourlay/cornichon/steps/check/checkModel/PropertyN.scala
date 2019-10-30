package com.github.agourlay.cornichon.steps.check.checkModel

import com.github.agourlay.cornichon.core.{ NoOpStep, NoValue, Step }

// N equals 6 for now
trait PropertyN[A, B, C, D, E, F] {
  val description: String
  val preCondition: Step
  val invariantN: (() => A, () => B, () => C, () => D, () => E, () => F) => Step
}

case class Property6[A, B, C, D, E, F](
    description: String,
    preCondition: Step = NoOpStep,
    invariant: (() => A, () => B, () => C, () => D, () => E, () => F) => Step) extends PropertyN[A, B, C, D, E, F] {
  override val invariantN: (() => A, () => B, () => C, () => D, () => E, () => F) => Step = invariant
}

case class Property5[A, B, C, D, E](
    description: String,
    preCondition: Step = NoOpStep,
    invariant: (() => A, () => B, () => C, () => D, () => E) => Step) extends PropertyN[A, B, C, D, E, NoValue] {
  override val invariantN: (() => A, () => B, () => C, () => D, () => E, () => NoValue) => Step =
    (a, b, c, d, e, _) => invariant(a, b, c, d, e)
}

case class Property4[A, B, C, D](
    description: String,
    preCondition: Step = NoOpStep,
    invariant: (() => A, () => B, () => C, () => D) => Step) extends PropertyN[A, B, C, D, NoValue, NoValue] {
  override val invariantN: (() => A, () => B, () => C, () => D, () => NoValue, () => NoValue) => Step =
    (a, b, c, d, _, _) => invariant(a, b, c, d)
}

case class Property3[A, B, C](
    description: String,
    preCondition: Step = NoOpStep,
    invariant: (() => A, () => B, () => C) => Step) extends PropertyN[A, B, C, NoValue, NoValue, NoValue] {
  override val invariantN: (() => A, () => B, () => C, () => NoValue, () => NoValue, () => NoValue) => Step =
    (a, b, c, _, _, _) => invariant(a, b, c)
}

case class Property2[A, B](
    description: String,
    preCondition: Step = NoOpStep,
    invariant: (() => A, () => B) => Step) extends PropertyN[A, B, NoValue, NoValue, NoValue, NoValue] {
  override val invariantN: (() => A, () => B, () => NoValue, () => NoValue, () => NoValue, () => NoValue) => Step =
    (a, b, _, _, _, _) => invariant(a, b)
}

case class Property1[A](
    description: String,
    preCondition: Step = NoOpStep,
    invariant: (() => A) => Step) extends PropertyN[A, NoValue, NoValue, NoValue, NoValue, NoValue] {
  override val invariantN: (() => A, () => NoValue, () => NoValue, () => NoValue, () => NoValue, () => NoValue) => Step =
    (a, _, _, _, _, _) => invariant(a)
}

case class Property0(
    description: String,
    preCondition: Step = NoOpStep,
    invariant: () => Step) extends PropertyN[NoValue, NoValue, NoValue, NoValue, NoValue, NoValue] {
  override val invariantN: (() => NoValue, () => NoValue, () => NoValue, () => NoValue, () => NoValue, () => NoValue) => Step =
    (_, _, _, _, _, _) => invariant()
}
