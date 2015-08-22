package com.github.agourlay.cornichon.core

class ScenarioBuilder {

  val steps = collection.mutable.ArrayBuffer.empty[Step[_]]

  def addStep(s: Step[_]) = {
    steps += s
    this
  }
}
