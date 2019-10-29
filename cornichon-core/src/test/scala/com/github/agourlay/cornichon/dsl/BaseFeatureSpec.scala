package com.github.agourlay.cornichon.dsl

import utest._

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }

object BaseFeatureSpec extends TestSuite {

  val tests = Tests {
    test("BaseFeature shutdowns resources") {
      var counter = 0
      val f1 = () => Future.successful({ counter = counter + 1 })
      BaseFeature.addShutdownHook(f1)
      val f2 = () => Future.successful({ counter = counter + 2 })
      BaseFeature.addShutdownHook(f2)
      Await.ready(BaseFeature.shutDownGlobalResources(), Duration.Inf)
      assert(counter == 3)
    }
  }

}
