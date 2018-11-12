package com.github.agourlay.cornichon.check

import com.github.agourlay.cornichon.core.Step

trait CheckDsl {

  def check_model[A, B, C, D, E, F](maxNumberOfRuns: Int, maxNumberOfTransitions: Int, seed: Option[Long] = None)(modelRunner: ModelRunner[A, B, C, D, E, F]): Step =
    CheckStep(maxNumberOfRuns, maxNumberOfTransitions, modelRunner, seed)

  //TODO
  def check_property[A](ga: Generator[A])(f: A ⇒ Step): Step = ???
  def check_property[A, B](ga: Generator[A], gb: Generator[B])(f: A ⇒ B ⇒ Step): Step = ???
  def check_property[A, B, C](ga: Generator[A], gb: Generator[B], gc: Generator[C])(f: A ⇒ B ⇒ C ⇒ Step): Step = ???
  // up to 6 args
}
