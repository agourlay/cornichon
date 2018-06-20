package com.github.agourlay.cornichon.check

import com.github.agourlay.cornichon.core.Step
import com.github.agourlay.cornichon.steps.regular.assertStep.AssertStep

case class Model[A, B, C, D, E, F](
    description: String,
    startingState: State[A, B, C, D, E, F],
    transitions: Map[State[A, B, C, D, E, F], List[(Double, State[A, B, C, D, E, F])]])

case class State[A, B, C, D, E, F](
    description: String,
    preConditions: List[AssertStep],
    action: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ E, () ⇒ F) ⇒ Step,
    postConditions: List[AssertStep])

case class ModelRunner[A, B, C, D, E, F](
    generatorA: Generator[A],
    generatorB: Generator[B],
    generatorC: Generator[C],
    generatorD: Generator[D],
    generatorE: Generator[E],
    generatorF: Generator[F],
    model: Model[A, B, C, D, E, F])

object ModelRunner {

  def make[A](genA: Generator[A])(model: Model[A, NoValue.type, NoValue.type, NoValue.type, NoValue.type, NoValue.type]): ModelRunner[A, NoValue.type, NoValue.type, NoValue.type, NoValue.type, NoValue.type] =
    ModelRunner(genA, NoValueGenerator, NoValueGenerator, NoValueGenerator, NoValueGenerator, NoValueGenerator, model)

  def make[A, B](genA: Generator[A], genB: Generator[B])(model: Model[A, B, NoValue.type, NoValue.type, NoValue.type, NoValue.type]) =
    ModelRunner(genA, genB, NoValueGenerator, NoValueGenerator, NoValueGenerator, NoValueGenerator, model)

  def make[A, B, C](genA: Generator[A], genB: Generator[B], genC: Generator[C])(model: Model[A, B, C, NoValue.type, NoValue.type, NoValue.type]) =
    ModelRunner(genA, genB, genC, NoValueGenerator, NoValueGenerator, NoValueGenerator, model)

}