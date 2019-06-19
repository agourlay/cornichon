package com.github.agourlay.cornichon.check.checkModel

import com.github.agourlay.cornichon.check.{ Generator, NoValue }
import com.github.agourlay.cornichon.check.NoValue.seededNoValueGenerator
import com.github.agourlay.cornichon.core.RandomContext

case class ModelRunner[A, B, C, D, E, F](
    generatorA: RandomContext ⇒ Generator[A],
    generatorB: RandomContext ⇒ Generator[B],
    generatorC: RandomContext ⇒ Generator[C],
    generatorD: RandomContext ⇒ Generator[D],
    generatorE: RandomContext ⇒ Generator[E],
    generatorF: RandomContext ⇒ Generator[F],
    model: Model[A, B, C, D, E, F])

case class Model[A, B, C, D, E, F](
    description: String,
    entryPoint: PropertyN[A, B, C, D, E, F],
    transitions: Map[PropertyN[A, B, C, D, E, F], List[(Int, PropertyN[A, B, C, D, E, F])]])

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