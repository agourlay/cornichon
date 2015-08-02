package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.dsl.HttpDsl
import com.github.agourlay.cornichon.http.HttpFeature
import org.scalatest.{ Matchers, WordSpec }

abstract class CornichonFeature extends WordSpec with Matchers with HttpFeature with HttpDsl {

  def failedFeatureErrorMsg(report: FailedFeatureReport): Seq[String] = {
    report.failedScenariosResult.map { r ⇒
      s"""Scenario "${r.scenarioName}" failed
         |at step "${r.failedStep.step}"
         |with error "${r.failedStep.error.msg}" """.stripMargin
    }
  }

  featureName must {
    "execute scenarios" in {
      val featureExecution = runFeature()
      featureExecution match {
        case s: SuccessFeatureReport ⇒
          assert(true)
        case f: FailedFeatureReport ⇒
          val msg = failedFeatureErrorMsg(f).mkString(" ")
          fail(msg)
      }
    }
  }
}
