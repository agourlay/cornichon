package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.core.{ FailedScenarioReport, FailedFeatureReport, FeatureReport }

trait ScenarioUtilSpec {

  def printlnFailedScenario(res: FeatureReport): Unit = {
    if (!res.success)
      res match {
        case FailedFeatureReport(f) ⇒
          f.foreach {
            case f: FailedScenarioReport ⇒ println("Failed Step " + f.failedStep)
          }
        case _ ⇒ ()
      }
  }
}
