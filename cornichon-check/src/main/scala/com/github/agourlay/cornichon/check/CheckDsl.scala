package com.github.agourlay.cornichon.check

import com.github.agourlay.cornichon.core.Step

trait CheckDsl {

  def check_model[A, B, C, D, E, F](maxNumberOfRuns: Int, maxNumberOfTransitions: Int, seed: Option[Long] = None)(modelRunner: ModelRunner[A, B, C, D, E, F]): Step =
    CheckStep(maxNumberOfRuns, maxNumberOfTransitions, modelRunner, seed)
}
