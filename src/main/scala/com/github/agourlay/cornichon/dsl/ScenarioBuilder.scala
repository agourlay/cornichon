package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core.Step

class ScenarioBuilder {

  val steps = collection.mutable.ArrayBuffer.empty[Step]

  def addStep(s: Step) = {
    steps += s
    this
  }
}
