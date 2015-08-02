package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.core.{ FailedFeatureReport, FeatureReport }

trait ScenarioUtilSpec {

  def printlnFailedScenario(res: FeatureReport): Unit = {
    if (!res.success)
      res match {
        case FailedFeatureReport(s, f) ⇒
          f.foreach { r ⇒ println("Failed Step " + r.failedStep) }
      }
  }
}
