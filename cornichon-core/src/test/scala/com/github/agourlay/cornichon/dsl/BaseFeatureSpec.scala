package com.github.agourlay.cornichon.dsl

import org.scalatest._

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }

class BaseFeatureSpec extends WordSpec with Matchers {

  "BaseFeature" must {
    "shutDownGlobalResources" in {
      var counter = 0
      val f1 = () ⇒ Future.successful({ counter = counter + 1 })
      BaseFeature.addShutdownHook(f1)
      val f2 = () ⇒ Future.successful({ counter = counter + 2 })
      BaseFeature.addShutdownHook(f2)
      Await.ready(BaseFeature.shutDownGlobalResources(), Duration.Inf)
      counter should be(3)
    }
  }

}
