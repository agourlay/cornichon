package com.github.agourlay.cornichon.http.steps

import com.github.agourlay.cornichon.core.SessionKey
import com.github.agourlay.cornichon.http.server.HttpMockServerResource.SessionKeys._
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }

object HttpListenSteps {

  case class HttpListenStepBuilder(name: String) extends AnyVal {
    def received_calls(count: Int): AssertStep = AssertStep(
      title = s"HTTP mock server '$name' received '$count' calls",
      action = sc => Assertion.either {
        sc.session.get(s"$name$nbReceivedCallsSuffix").map(c => GenericEqualityAssertion(count, c.toInt))
      }
    )

    def received_requests: JsonStepBuilder =
      JsonStepBuilder(
        SessionKey(s"$name$receivedBodiesSuffix"),
        prettySessionKeyTitle = Some(s"HTTP mock server '$name' received requests")
      )
  }

}
