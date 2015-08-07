package com.github.agourlay.cornichon.core

import org.scalatest.{ Matchers, WordSpec }

trait ScalatestIntegration extends WordSpec with Matchers {
  this: Feature ⇒

  feat.name must {
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
