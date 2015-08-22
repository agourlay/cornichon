package com.github.agourlay.cornichon.core

trait ScenarioUtilSpec {

  def printlnFailedScenario(res: FeatureReport): Unit = {
    if (!res.success)
      res match {
        case FailedFeatureReport(f, errors) ⇒ println(errors.mkString(" "))
        case _                              ⇒ ()
      }
  }
}
