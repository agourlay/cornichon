package com.github.agourlay.cornichon.check

import com.github.agourlay.cornichon.core.Step

trait CheckDsl {

  def checkModel(model: Model): Step =
    CheckStep(model)

}
