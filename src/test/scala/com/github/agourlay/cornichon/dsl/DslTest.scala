package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.http.HttpFeature

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

// implement this trait for dsl test
trait DslTest extends HttpFeature with HttpDsl {
  lazy val requestTimeout: FiniteDuration = 2000 millis
}
