package com.github.agourlay.cornichon.core

import org.scalatest.{ Matchers, WordSpec }

trait ScalatestIntegration extends WordSpec with Matchers {
  this: Feature ⇒

  feat.name must {
    "pass all scenarios" in {
      val featureExecution = runFeature()
      featureExecution match {
        case s: SuccessFeatureReport ⇒
          assert(true)
        case FailedFeatureReport(_, errors) ⇒
          fail(errors.mkString(" "))
      }
    }
  }
}
