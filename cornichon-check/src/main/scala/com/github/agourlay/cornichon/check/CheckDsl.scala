package com.github.agourlay.cornichon.check

import com.github.agourlay.cornichon.check.checkModel.{ CheckModelStep, ModelRunner }
import com.github.agourlay.cornichon.check.forAll.ForAllStep1
import com.github.agourlay.cornichon.core.Step

trait CheckDsl {

  def check_model[A, B, C, D, E, F](maxNumberOfRuns: Int, maxNumberOfTransitions: Int, seed: Option[Long] = None)(modelRunner: ModelRunner[A, B, C, D, E, F]): Step =
    CheckModelStep(maxNumberOfRuns, maxNumberOfTransitions, modelRunner, seed)

  def for_all[A](description: String, maxNumberOfRuns: Int, ga: RandomContext ⇒ Generator[A])(f: A ⇒ Step): Step =
    new ForAllStep1[A](description, maxNumberOfRuns)(ga, f)

  // TODO Find a clean way to not have 6 concrete step implementations

  def for_all[A, B](description: String, maxNumberOfRuns: Int, ga: RandomContext ⇒ Generator[A], gb: RandomContext ⇒ Generator[B])(f: A ⇒ B ⇒ Step): Step =
    ???

  def for_all[A, B, C](description: String, maxNumberOfRuns: Int, ga: RandomContext ⇒ Generator[A], gb: RandomContext ⇒ Generator[B], gc: RandomContext ⇒ Generator[C])(f: A ⇒ B ⇒ C ⇒ Step): Step =
    ???

  def for_all[A, B, C, D](description: String, maxNumberOfRuns: Int, ga: RandomContext ⇒ Generator[A], gb: RandomContext ⇒ Generator[B], gc: RandomContext ⇒ Generator[C], gd: RandomContext ⇒ Generator[D])(f: A ⇒ B ⇒ C ⇒ D ⇒ Step): Step =
    ???

  def for_all[A, B, C, D, E](description: String, maxNumberOfRuns: Int, ga: RandomContext ⇒ Generator[A], gb: RandomContext ⇒ Generator[B], gc: RandomContext ⇒ Generator[C], gd: RandomContext ⇒ Generator[D], ge: RandomContext ⇒ Generator[E])(f: A ⇒ B ⇒ C ⇒ D ⇒ E ⇒ Step): Step =
    ???

  def for_all[A, B, C, D, E, F](description: String, maxNumberOfRuns: Int, ga: RandomContext ⇒ Generator[A], gb: RandomContext ⇒ Generator[B], gc: RandomContext ⇒ Generator[C], gd: RandomContext ⇒ Generator[D], ge: RandomContext ⇒ Generator[E], gf: RandomContext ⇒ Generator[F])(f: A ⇒ B ⇒ C ⇒ D ⇒ E ⇒ F ⇒ Step): Step =
    ???

}
