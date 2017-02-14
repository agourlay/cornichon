package com.github.agourlay.cornichon.http.steps

import cats.instances.int._
import com.github.agourlay.cornichon.core.SessionKey
import com.github.agourlay.cornichon.http.server.HttpMockServerResource.SessionKeys._
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder
import com.github.agourlay.cornichon.resolver.Resolver
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }

object HttpListenSteps {

  case class HttpListenStepBuilder(name: String, resolver: Resolver) {
    def received_calls(count: Int) = AssertStep(
      title = s"HTTP mock server '$name' received '$count' calls",
      action = s ⇒ GenericEqualityAssertion(count, s.get(s"$name$nbReceivedCallsSuffix").toInt)
    )

    def received_requests =
      JsonStepBuilder(
        resolver,
        SessionKey(s"$name$receivedBodiesSuffix"),
        prettySessionKeyTitle = Some(s"HTTP mock server '$name' received requests")
      )
  }

}
