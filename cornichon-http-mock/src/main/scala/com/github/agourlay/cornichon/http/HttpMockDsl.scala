package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.core.Step
import com.github.agourlay.cornichon.dsl.BodyElementCollector
import com.github.agourlay.cornichon.feature.BaseFeature
import com.github.agourlay.cornichon.http.server.HttpMockServerResource
import com.github.agourlay.cornichon.http.steps.HttpListenSteps.HttpListenStepBuilder
import com.github.agourlay.cornichon.steps.wrapped.WithBlockScopedResource

trait HttpMockDsl {
  this: BaseFeature ⇒

  def httpListen(label: String) = HttpListenStepBuilder(label, placeholderResolver, matcherResolver)

  def HttpListenTo(interface: Option[String], portRange: Option[Range])(label: String) =
    BodyElementCollector[Step, Step] { steps ⇒
      WithBlockScopedResource(nested = steps, resource = HttpMockServerResource(interface, label, portRange))
    }

}
