package com.github.agourlay.cornichon.http.steps

import cats.instances.int._
import cats.syntax.either._
import com.github.agourlay.cornichon.core.SessionKey
import com.github.agourlay.cornichon.http.server.HttpMockServerResource.SessionKeys._
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder
import com.github.agourlay.cornichon.matchers.MatcherResolver
import com.github.agourlay.cornichon.resolver.PlaceholderResolver
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }

object HttpListenSteps {

  case class HttpListenStepBuilder(name: String, placeholderResolver: PlaceholderResolver, matcherResolver: MatcherResolver) {
    def received_calls(count: Int) = AssertStep(
      title = s"HTTP mock server '$name' received '$count' calls",
      action = s ⇒ Assertion.either {
        s.get(s"$name$nbReceivedCallsSuffix").map(c ⇒ GenericEqualityAssertion(count, c.toInt))
      }
    )

    def received_requests =
      JsonStepBuilder(
        placeholderResolver,
        matcherResolver,
        SessionKey(s"$name$receivedBodiesSuffix"),
        prettySessionKeyTitle = Some(s"HTTP mock server '$name' received requests")
      )
  }

}
