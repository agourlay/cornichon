package com.github.agourlay.cornichon.check

import com.github.agourlay.cornichon.core.{ NoOpStep, Step }
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
    entryPoint: PropertyN[A, B, C, D, E, F],
    transitions: Map[PropertyN[A, B, C, D, E, F], List[(Double, PropertyN[A, B, C, D, E, F])]])

// N equals 6 for now
trait PropertyN[A, B, C, D, E, F] {
  val description: String
  val preCondition: Step
  val invariantN: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ E, () ⇒ F) ⇒ Step
}

case class Property6[A, B, C, D, E, F](
    description: String,
    preCondition: Step = NoOpStep,
    invariant: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ E, () ⇒ F) ⇒ Step) extends PropertyN[A, B, C, D, E, F] {
  override val invariantN: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ E, () ⇒ F) ⇒ Step = invariant
}

case class Property5[A, B, C, D, E](
    description: String,
    preCondition: Step = NoOpStep,
    invariant: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ E) ⇒ Step) extends PropertyN[A, B, C, D, E, NoValue] {
  override val invariantN: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ E, () ⇒ NoValue) ⇒ Step =
    (a, b, c, d, e, _) ⇒ invariant(a, b, c, d, e)
}

case class Property4[A, B, C, D](
    description: String,
    preCondition: Step = NoOpStep,
    invariant: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D) ⇒ Step) extends PropertyN[A, B, C, D, NoValue, NoValue] {
  override val invariantN: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ D, () ⇒ NoValue, () ⇒ NoValue) ⇒ Step =
    (a, b, c, d, _, _) ⇒ invariant(a, b, c, d)
}

case class Property3[A, B, C](
    description: String,
    preCondition: Step = NoOpStep,
    effect: (() ⇒ A, () ⇒ B, () ⇒ C) ⇒ Step) extends PropertyN[A, B, C, NoValue, NoValue, NoValue] {
  override val invariantN: (() ⇒ A, () ⇒ B, () ⇒ C, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue) ⇒ Step =
    (a, b, c, _, _, _) ⇒ effect(a, b, c)
}

case class Property2[A, B](
    description: String,
    preCondition: Step = NoOpStep,
    invariant: (() ⇒ A, () ⇒ B) ⇒ Step) extends PropertyN[A, B, NoValue, NoValue, NoValue, NoValue] {
  override val invariantN: (() ⇒ A, () ⇒ B, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue) ⇒ Step =
    (a, b, _, _, _, _) ⇒ invariant(a, b)
}

case class Property1[A](
    description: String,
    preCondition: Step = NoOpStep,
    invariant: (() ⇒ A) ⇒ Step) extends PropertyN[A, NoValue, NoValue, NoValue, NoValue, NoValue] {
  override val invariantN: (() ⇒ A, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue) ⇒ Step =
    (a, _, _, _, _, _) ⇒ invariant(a)
}

case class Property0(
    description: String,
    preCondition: Step = NoOpStep,
    invariant: () ⇒ Step) extends PropertyN[NoValue, NoValue, NoValue, NoValue, NoValue, NoValue] {
  override val invariantN: (() ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue, () ⇒ NoValue) ⇒ Step =
    (_, _, _, _, _, _) ⇒ invariant()
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