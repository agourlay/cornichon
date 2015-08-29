package com.github.agourlay.cornichon.core

class ScenarioBuilder {

  val steps = collection.mutable.ArrayBuffer.empty[Step]

  def addStep(s: Step) = {
    steps += s
    this
  }
}
