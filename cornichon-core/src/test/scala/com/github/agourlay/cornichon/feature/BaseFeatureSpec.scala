package com.github.agourlay.cornichon.feature

import org.scalatest._

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }

class CornichonBaseFeatureSpec extends WordSpec with Matchers {

  "BaseFeature" must {
    "shutDownGlobalResources" in {
      var counter = 0
      val f1 = () ⇒ Future.successful({ counter = counter + 1 })
      CornichonBaseFeature.addShutdownHook(f1)
      val f2 = () ⇒ Future.successful({ counter = counter + 2 })
      CornichonBaseFeature.addShutdownHook(f2)
      Await.ready(CornichonBaseFeature.shutDownGlobalResources(), Duration.Inf)
      counter should be(3)
    }
  }

}
