package com.github.agourlay.cornichon.check

import com.github.agourlay.cornichon.check.checkModel.{ CheckModelStep, ModelRunner }
import com.github.agourlay.cornichon.check.forAll.ForAllStep
import com.github.agourlay.cornichon.core.{ RandomContext, Step }
import com.github.agourlay.cornichon.check.NoValue.seededNoValueGenerator

trait CheckDsl {

  def check_model[A, B, C, D, E, F](maxNumberOfRuns: Int, maxNumberOfTransitions: Int)(modelRunner: ModelRunner[A, B, C, D, E, F]): Step =
    CheckModelStep(maxNumberOfRuns, maxNumberOfTransitions, modelRunner)

  def for_all[A](description: String, maxNumberOfRuns: Int, ga: RandomContext => Generator[A])(builder: A => Step): Step = {
    val g: A => NoValue => NoValue => NoValue => NoValue => NoValue => Step = { a: A => _ => _ => _ => _ => _ => builder(a) }
    ForAllStep[A, NoValue, NoValue, NoValue, NoValue, NoValue](description, maxNumberOfRuns)(ga, seededNoValueGenerator, seededNoValueGenerator, seededNoValueGenerator, seededNoValueGenerator, seededNoValueGenerator)(g)
  }

  def for_all[A, B](description: String, maxNumberOfRuns: Int, ga: RandomContext => Generator[A], gb: RandomContext => Generator[B])(builder: A => B => Step): Step = {
    val g: A => B => NoValue => NoValue => NoValue => NoValue => Step = { a: A => b: B => _ => _ => _ => _ => builder(a)(b) }
    ForAllStep[A, B, NoValue, NoValue, NoValue, NoValue](description, maxNumberOfRuns)(ga, gb, seededNoValueGenerator, seededNoValueGenerator, seededNoValueGenerator, seededNoValueGenerator)(g)
  }

  def for_all[A, B, C](description: String, maxNumberOfRuns: Int, ga: RandomContext => Generator[A], gb: RandomContext => Generator[B], gc: RandomContext => Generator[C])(builder: A => B => C => Step): Step = {
    val g: A => B => C => NoValue => NoValue => NoValue => Step = { a: A => b: B => c: C => _ => _ => _ => builder(a)(b)(c) }
    ForAllStep[A, B, C, NoValue, NoValue, NoValue](description, maxNumberOfRuns)(ga, gb, gc, seededNoValueGenerator, seededNoValueGenerator, seededNoValueGenerator)(g)
  }

  def for_all[A, B, C, D](description: String, maxNumberOfRuns: Int, ga: RandomContext => Generator[A], gb: RandomContext => Generator[B], gc: RandomContext => Generator[C], gd: RandomContext => Generator[D])(builder: A => B => C => D => Step): Step = {
    val g: A => B => C => D => NoValue => NoValue => Step = { a: A => b: B => c: C => d: D => _ => _ => builder(a)(b)(c)(d) }
    ForAllStep[A, B, C, D, NoValue, NoValue](description, maxNumberOfRuns)(ga, gb, gc, gd, seededNoValueGenerator, seededNoValueGenerator)(g)
  }

  def for_all[A, B, C, D, E](description: String, maxNumberOfRuns: Int, ga: RandomContext => Generator[A], gb: RandomContext => Generator[B], gc: RandomContext => Generator[C], gd: RandomContext => Generator[D], ge: RandomContext => Generator[E])(builder: A => B => C => D => E => Step): Step = {
    val g: A => B => C => D => E => NoValue => Step = { a: A => b: B => c: C => d: D => e: E => _ => builder(a)(b)(c)(d)(e) }
    ForAllStep[A, B, C, D, E, NoValue](description, maxNumberOfRuns)(ga, gb, gc, gd, ge, seededNoValueGenerator)(g)
  }

  def for_all[A, B, C, D, E, F](description: String, maxNumberOfRuns: Int, ga: RandomContext => Generator[A], gb: RandomContext => Generator[B], gc: RandomContext => Generator[C], gd: RandomContext => Generator[D], ge: RandomContext => Generator[E], gf: RandomContext => Generator[F])(builder: A => B => C => D => E => F => Step): Step = {
    val g: A => B => C => D => E => F => Step = { a: A => b: B => c: C => d: D => e: E => f: F => builder(a)(b)(c)(d)(e)(f) }
    ForAllStep[A, B, C, D, E, F](description, maxNumberOfRuns)(ga, gb, gc, gd, ge, gf)(g)
  }

}
