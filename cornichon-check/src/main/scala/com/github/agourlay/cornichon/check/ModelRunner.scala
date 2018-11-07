package com.github.agourlay.cornichon.check

import com.github.agourlay.cornichon.core.Step
import com.github.agourlay.cornichon.check.NoValue.seededNoValueGenerator

import scala.util.Random

case class ModelRunner[A, B, C, D, E, F](
    generatorA: RandomContext ⇒ Generator[A],
    generatorB: RandomContext ⇒ Generator[B],
    generatorC: RandomContext ⇒ Generator[C],
    generatorD: RandomContext ⇒ Generator[D],
    generatorE: RandomContext ⇒ Generator[E],
    generatorF: RandomContext ⇒ Generator[F],
    model: Model[A, B, C, D, E, F])

case class RandomContext(seed: Long, seededRandom: Random)

case class Model[A, B, C, D, E, F](
    description: String,
    startingAction: ActionN[A, B, C, D, E, F],
    transitions: Map[ActionN[A, B, C, D, E, F], List[(Double, ActionN[A, B, C, D, E, F])]])

// N equals 6 for now
trait ActionN[A, B, C, D, E, F] {
  val description: String
  val preConditions: List[Step]
  val effectN: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ E, () ⇒ F) ⇒ Step
  val postConditions: List[Step]
}

case class Action6[A, B, C, D, E, F](
    description: String,
    preConditions: List[Step] = Nil,
    effect: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ E, () ⇒ F) ⇒ Step,
    postConditions: List[Step]) extends ActionN[A, B, C, D, E, F] {
  override val effectN: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ E, () ⇒ F) ⇒ Step = effect
}

case class Action5[A, B, C, D, E](
    description: String,
    preConditions: List[Step] = Nil,
    effect: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ E) ⇒ Step,
    postConditions: List[Step]) extends ActionN[A, B, C, D, E, NoValue] {
  override val effectN: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ E, () ⇒ NoValue) ⇒ Step =
    (a, b, c, d, e, _) ⇒ effect(a, b, c, d, e)
}

case class Action4[A, B, C, D](
    description: String,
    preConditions: List[Step] = Nil,
    effect: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D) ⇒ Step,
    postConditions: List[Step]) extends ActionN[A, B, C, D, NoValue, NoValue] {
  override val effectN: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ NoValue, () ⇒ NoValue) ⇒ Step =
    (a, b, c, d, _, _) ⇒ effect(a, b, c, d)
}

case class Action3[A, B, C](
    description: String,
    preConditions: List[Step] = Nil,
    effect: (() ⇒ A, () ⇒ B, () ⇒ C) ⇒ Step,
    postConditions: List[Step]) extends ActionN[A, B, C, NoValue, NoValue, NoValue] {
  override val effectN: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue) ⇒ Step =
    (a, b, c, _, _, _) ⇒ effect(a, b, c)
}

case class Action2[A, B](
    description: String,
    preConditions: List[Step] = Nil,
    effect: (() ⇒ A, () ⇒ B) ⇒ Step,
    postConditions: List[Step]) extends ActionN[A, B, NoValue, NoValue, NoValue, NoValue] {
  override val effectN: (() ⇒ A, () ⇒ B, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue) ⇒ Step =
    (a, b, _, _, _, _) ⇒ effect(a, b)
}

case class Action1[A](
    description: String,
    preConditions: List[Step] = Nil,
    effect: (() ⇒ A) ⇒ Step,
    postConditions: List[Step]) extends ActionN[A, NoValue, NoValue, NoValue, NoValue, NoValue] {
  override val effectN: (() ⇒ A, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue) ⇒ Step =
    (a, _, _, _, _, _) ⇒ effect(a)
}

case class Action0(
    description: String,
    preConditions: List[Step] = Nil,
    effect: () ⇒ Step,
    postConditions: List[Step]) extends ActionN[NoValue, NoValue, NoValue, NoValue, NoValue, NoValue] {
  override val effectN: (() ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue) ⇒ Step =
    (_, _, _, _, _, _) ⇒ effect()
}

object ModelRunner {

  def makeNoGen(model: Model[NoValue, NoValue, NoValue, NoValue, NoValue, NoValue]): ModelRunner[NoValue, NoValue, NoValue, NoValue, NoValue, NoValue] =
    ModelRunner(seededNoValueGenerator, seededNoValueGenerator, seededNoValueGenerator, seededNoValueGenerator, seededNoValueGenerator, seededNoValueGenerator, model)

  def make[A](genA: RandomContext ⇒ Generator[A])(model: Model[A, NoValue, NoValue, NoValue, NoValue, NoValue]): ModelRunner[A, NoValue, NoValue, NoValue, NoValue, NoValue] =
    ModelRunner(genA, seededNoValueGenerator, seededNoValueGenerator, seededNoValueGenerator, seededNoValueGenerator, seededNoValueGenerator, model)

  def make[A, B](genA: RandomContext ⇒ Generator[A], genB: RandomContext ⇒ Generator[B])(model: Model[A, B, NoValue, NoValue, NoValue, NoValue]) =
    ModelRunner(genA, genB, seededNoValueGenerator, seededNoValueGenerator, seededNoValueGenerator, seededNoValueGenerator, model)

  def make[A, B, C](genA: RandomContext ⇒ Generator[A], genB: RandomContext ⇒ Generator[B], genC: RandomContext ⇒ Generator[C])(model: Model[A, B, C, NoValue, NoValue, NoValue]) =
    ModelRunner(genA, genB, genC, seededNoValueGenerator, seededNoValueGenerator, seededNoValueGenerator, model)

  def make[A, B, C, D](genA: RandomContext ⇒ Generator[A], genB: RandomContext ⇒ Generator[B], genC: RandomContext ⇒ Generator[C], genD: RandomContext ⇒ Generator[D])(model: Model[A, B, C, D, NoValue, NoValue]) =
    ModelRunner(genA, genB, genC, genD, seededNoValueGenerator, seededNoValueGenerator, model)

  def make[A, B, C, D, E](genA: RandomContext ⇒ Generator[A], genB: RandomContext ⇒ Generator[B], genC: RandomContext ⇒ Generator[C], genD: RandomContext ⇒ Generator[D], genE: RandomContext ⇒ Generator[E])(model: Model[A, B, C, D, E, NoValue]) =
    ModelRunner(genA, genB, genC, genD, genE, seededNoValueGenerator, model)

  def make[A, B, C, D, E, F](genA: RandomContext ⇒ Generator[A], genB: RandomContext ⇒ Generator[B], genC: RandomContext ⇒ Generator[C], genD: RandomContext ⇒ Generator[D], genE: RandomContext ⇒ Generator[E], genF: RandomContext ⇒ Generator[F])(model: Model[A, B, C, D, E, F]) =
    ModelRunner(genA, genB, genC, genD, genE, genF, model)

}