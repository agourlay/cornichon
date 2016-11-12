package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.core.SessionKey
import com.github.agourlay.cornichon.http.HttpAssertionErrors._
import com.github.agourlay.cornichon.http.HttpService.SessionKeys._
import com.github.agourlay.cornichon.http.server.HttpMockServerResource.SessionKeys._
import com.github.agourlay.cornichon.json.JsonAssertions.JsonAssertion
import com.github.agourlay.cornichon.resolver.Resolver
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, CollectionSizeAssertion, CustomMessageEqualityAssertion, GenericEqualityAssertion }
import com.github.agourlay.cornichon.util.Instances._

object HttpAssertions {

  case object StatusAssertion {
    def is(expected: Int) = AssertStep(
      title = s"status is '$expected'",
      action = s ⇒ CustomMessageEqualityAssertion(
      expected = expected,
      actual = s.get(lastResponseStatusKey).toInt,
      customMessage = statusError(expected, s.get(lastResponseBodyKey))
    )
    )
  }

  case class HeadersAssertion(private val ordered: Boolean) {
    def is(expected: (String, String)*) = AssertStep(
      title = s"headers is ${displayStringPairs(expected)}",
      action = s ⇒
      GenericEqualityAssertion.fromSession(s, SessionKey(lastResponseHeadersKey)) { (session, sessionHeaders) ⇒
        val actualValue = sessionHeaders.split(",").toList
        val expectedValue = expected.toList.map { case (name, value) ⇒ s"$name$headersKeyValueDelim$value" }
        (expectedValue, actualValue)
      }
    )

    // TODO use detail assertion to show full headers
    def hasSize(expected: Int) = AssertStep(
      title = s"headers size is '$expected'",
      action = s ⇒ {
      val sessionHeaders = s.get(lastResponseHeadersKey)
      CollectionSizeAssertion(sessionHeaders.split(","), expected)
    }
    )

    def contain(elements: (String, String)*) = AssertStep(
      title = s"headers contain ${displayStringPairs(elements)}",
      action = s ⇒
      CustomMessageEqualityAssertion.fromSession(s, SessionKey(lastResponseHeadersKey)) { (session, sessionHeaders) ⇒
        val sessionHeadersValue = sessionHeaders.split(interHeadersValueDelim)
        val predicate = elements.forall { case (name, value) ⇒ sessionHeadersValue.contains(s"$name$headersKeyValueDelim$value") }
        (true, predicate, headersDoesNotContainError(displayStringPairs(elements), sessionHeaders))
      }
    )

    def inOrder: HeadersAssertion = copy(ordered = true)

    def name(name: String) = HeadersNameAssertion(name)
  }

  case class HeadersNameAssertion(name: String) {
    def isPresent = AssertStep(
      title = s"headers contain field with name '$name'",
      action = s ⇒
      CustomMessageEqualityAssertion.fromSession(s, SessionKey(lastResponseHeadersKey)) { (session, sessionHeaders) ⇒
        val sessionHeadersValue = HttpService.decodeSessionHeaders(sessionHeaders)
        val predicate = sessionHeadersValue.exists { case (hname, _) ⇒ hname == name }
        (true, predicate, headersDoesNotContainFieldWithNameError(name, sessionHeadersValue))
      }
    )

    def isAbsent = AssertStep(
      title = s"headers do not contain field with name '$name'",
      action = s ⇒
      CustomMessageEqualityAssertion.fromSession(s, SessionKey(lastResponseHeadersKey)) { (session, sessionHeaders) ⇒
        val sessionHeadersValue = HttpService.decodeSessionHeaders(sessionHeaders)
        val predicate = !sessionHeadersValue.exists { case (hname, _) ⇒ hname == name }
        (true, predicate, headersContainFieldWithNameError(name, sessionHeadersValue))
      }
    )
  }

  case class HttpListen(name: String, resolver: Resolver) {
    def received_calls(count: Int) = AssertStep(
      title = s"HTTP mock server '$name' received '$count' calls",
      action = s ⇒ GenericEqualityAssertion(expected = count, actual = s.get(s"$name$nbReceivedCallsSuffix").toInt)
    )

    def received_requests =
      JsonAssertion(resolver, SessionKey(s"$name$receivedBodiesSuffix"), prettySessionKeyTitle = Some(s"HTTP mock server '$name' received requests"))

  }
}
