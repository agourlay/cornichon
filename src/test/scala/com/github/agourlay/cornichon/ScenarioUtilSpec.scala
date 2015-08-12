package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.core.{ FailedFeatureReport, FeatureReport }

trait ScenarioUtilSpec {

  def printlnFailedScenario(res: FeatureReport): Unit = {
    if (!res.success)
      res match {
        case FailedFeatureReport(f, errors) ⇒ println(errors.mkString(" "))
        case _                              ⇒ ()
      }
  }
}
