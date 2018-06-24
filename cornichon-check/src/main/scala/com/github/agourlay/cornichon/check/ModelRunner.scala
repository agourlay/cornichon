package com.github.agourlay.cornichon.check

import com.github.agourlay.cornichon.core.Step
import com.github.agourlay.cornichon.steps.regular.assertStep.AssertStep

case class ModelRunner[A, B, C, D, E, F](
    generatorA: Generator[A],
    generatorB: Generator[B],
    generatorC: Generator[C],
    generatorD: Generator[D],
    generatorE: Generator[E],
    generatorF: Generator[F],
    model: Model[A, B, C, D, E, F])

case class Model[A, B, C, D, E, F](
    description: String,
    startingAction: ActionN[A, B, C, D, E, F],
    transitions: Map[ActionN[A, B, C, D, E, F], List[(Double, ActionN[A, B, C, D, E, F])]])

// N equals 6 for now
trait ActionN[A, B, C, D, E, F] {
  val description: String
  val preConditions: List[AssertStep]
  val effectN: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ E, () ⇒ F) ⇒ Step
  val postConditions: List[AssertStep]
}

case class Action6[A, B, C, D, E, F](
    description: String,
    preConditions: List[AssertStep],
    effect: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ E, () ⇒ F) ⇒ Step,
    postConditions: List[AssertStep]) extends ActionN[A, B, C, D, E, F] {
  override val effectN: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ E, () ⇒ F) ⇒ Step = effect
}

case class Action5[A, B, C, D, E](
    description: String,
    preConditions: List[AssertStep],
    effect: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ E) ⇒ Step,
    postConditions: List[AssertStep]) extends ActionN[A, B, C, D, E, NoValue] {
  override val effectN: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ E, () ⇒ NoValue) ⇒ Step =
    (a, b, c, d, e, _) ⇒ effect(a, b, c, d, e)
}

case class Action4[A, B, C, D](
    description: String,
    preConditions: List[AssertStep],
    effect: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D) ⇒ Step,
    postConditions: List[AssertStep]) extends ActionN[A, B, C, D, NoValue, NoValue] {
  override val effectN: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ NoValue, () ⇒ NoValue) ⇒ Step =
    (a, b, c, d, _, _) ⇒ effect(a, b, c, d)
}

case class Action3[A, B, C](
    description: String,
    preConditions: List[AssertStep],
    effect: (() ⇒ A, () ⇒ B, () ⇒ C) ⇒ Step,
    postConditions: List[AssertStep]) extends ActionN[A, B, C, NoValue, NoValue, NoValue] {
  override val effectN: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue) ⇒ Step =
    (a, b, c, _, _, _) ⇒ effect(a, b, c)
}

case class Action2[A, B](
    description: String,
    preConditions: List[AssertStep],
    effect: (() ⇒ A, () ⇒ B) ⇒ Step,
    postConditions: List[AssertStep]) extends ActionN[A, B, NoValue, NoValue, NoValue, NoValue] {
  override val effectN: (() ⇒ A, () ⇒ B, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue) ⇒ Step =
    (a, b, _, _, _, _) ⇒ effect(a, b)
}

case class Action1[A](
    description: String,
    preConditions: List[AssertStep],
    effect: (() ⇒ A) ⇒ Step,
    postConditions: List[AssertStep]) extends ActionN[A, NoValue, NoValue, NoValue, NoValue, NoValue] {
  override val effectN: (() ⇒ A, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue) ⇒ Step =
    (a, _, _, _, _, _) ⇒ effect(a)
}

case class Action0[A](
    description: String,
    preConditions: List[AssertStep],
    effect: () ⇒ Step,
    postConditions: List[AssertStep]) extends ActionN[NoValue, NoValue, NoValue, NoValue, NoValue, NoValue] {
  override val effectN: (() ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue) ⇒ Step =
    (_, _, _, _, _, _) ⇒ effect()
}

object ModelRunner {

  def make[A](genA: Generator[A])(model: Model[A, NoValue, NoValue, NoValue, NoValue, NoValue]): ModelRunner[A, NoValue, NoValue, NoValue, NoValue, NoValue] =
    ModelRunner(genA, NoValueGenerator, NoValueGenerator, NoValueGenerator, NoValueGenerator, NoValueGenerator, model)

  def make[A, B](genA: Generator[A], genB: Generator[B])(model: Model[A, B, NoValue, NoValue, NoValue, NoValue]) =
    ModelRunner(genA, genB, NoValueGenerator, NoValueGenerator, NoValueGenerator, NoValueGenerator, model)

  def make[A, B, C](genA: Generator[A], genB: Generator[B], genC: Generator[C])(model: Model[A, B, C, NoValue, NoValue, NoValue]) =
    ModelRunner(genA, genB, genC, NoValueGenerator, NoValueGenerator, NoValueGenerator, model)

  def make[A, B, C, D](genA: Generator[A], genB: Generator[B], genC: Generator[C], genD: Generator[D])(model: Model[A, B, C, D, NoValue, NoValue]) =
    ModelRunner(genA, genB, genC, genD, NoValueGenerator, NoValueGenerator, model)

  def make[A, B, C, D, E](genA: Generator[A], genB: Generator[B], genC: Generator[C], genD: Generator[D], genE: Generator[E])(model: Model[A, B, C, D, E, NoValue]) =
    ModelRunner(genA, genB, genC, genD, genE, NoValueGenerator, model)

  def make[A, B, C, D, E, F](genA: Generator[A], genB: Generator[B], genC: Generator[C], genD: Generator[D], genE: Generator[E], genF: Generator[F])(model: Model[A, B, C, D, E, F]) =
    ModelRunner(genA, genB, genC, genD, genE, genF, model)

}