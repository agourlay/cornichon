package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.CornichonFeature
import org.scalatest.FreeSpecLike

trait ScalaTestIntegration extends FreeSpecLike {
  this: CornichonFeature ⇒

  feature.name in {
    runFeature() match {
      case s: SuccessFeatureReport ⇒
        assert(true)
      case FailedFeatureReport(_, _, errors) ⇒
        fail(errors.mkString(" "))
    }
  }
}
