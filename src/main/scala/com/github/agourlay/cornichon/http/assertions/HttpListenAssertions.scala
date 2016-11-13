package com.github.agourlay.cornichon.http.assertions

import com.github.agourlay.cornichon.core.SessionKey
import com.github.agourlay.cornichon.http.server.HttpMockServerResource.SessionKeys._
import com.github.agourlay.cornichon.json.JsonAssertions.JsonAssertion
import com.github.agourlay.cornichon.resolver.Resolver
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import com.github.agourlay.cornichon.util.Instances._

object HttpListenAssertions {

  case class HttpListen(name: String, resolver: Resolver) {
    def received_calls(count: Int) = AssertStep(
      title = s"HTTP mock server '$name' received '$count' calls",
      action = s ⇒ GenericEqualityAssertion(count, s.get(s"$name$nbReceivedCallsSuffix").toInt)
    )

    def received_requests =
      JsonAssertion(
        resolver,
        SessionKey(s"$name$receivedBodiesSuffix"),
        prettySessionKeyTitle = Some(s"HTTP mock server '$name' received requests")
      )
  }

}
