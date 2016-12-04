package com.github.agourlay.cornichon.http.steps

import com.github.agourlay.cornichon.core.SessionKey
import com.github.agourlay.cornichon.http.HttpService
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.http.HttpService.SessionKeys._
import com.github.agourlay.cornichon.steps.regular.assertStep._
import com.github.agourlay.cornichon.util.Instances._

object HeadersSteps {
  private val headersSessionKey = SessionKey(lastResponseHeadersKey)

  case class HeadersStepBuilder(private val ordered: Boolean) {
    def is(expected: (String, String)*) = AssertStep(
      title = s"headers is ${displayStringPairs(expected)}",
      action = s ⇒ {
      val sessionHeaders = s.get(headersSessionKey)
      val actualValue = sessionHeaders.split(",").toList
      val expectedValue = expected.toList.map { case (name, value) ⇒ s"$name$headersKeyValueDelim$value" }
      GenericEqualityAssertion(expectedValue, actualValue)
    }
    )

    def hasSize(expectedSize: Int) = AssertStep(
      title = s"headers size is '$expectedSize'",
      action = s ⇒ CollectionSizeAssertion(s.get(headersSessionKey).split(","), expectedSize).withName("headers")
    )

    def contain(elements: (String, String)*) = AssertStep(
      title = s"headers contain ${displayStringPairs(elements)}",
      action = s ⇒
      CustomMessageEqualityAssertion.fromSession(s, headersSessionKey) { (session, sessionHeaders) ⇒
        val sessionHeadersValue = sessionHeaders.split(interHeadersValueDelim)
        val predicate = elements.forall { case (name, value) ⇒ sessionHeadersValue.contains(s"$name$headersKeyValueDelim$value") }
        (true, predicate, headersDoesNotContainError(displayStringPairs(elements), sessionHeaders))
      }
    )

    def inOrder: HeadersStepBuilder = copy(ordered = true)

    def name(name: String) = HeadersNameStepBuilder(name)
  }

  case class HeadersNameStepBuilder(name: String) {
    def isPresent = AssertStep(
      title = s"headers contain field with name '$name'",
      action = s ⇒
      CustomMessageEqualityAssertion.fromSession(s, headersSessionKey) { (session, sessionHeaders) ⇒
        val sessionHeadersValue = HttpService.decodeSessionHeaders(sessionHeaders)
        val predicate = sessionHeadersValue.exists { case (hname, _) ⇒ hname == name }
        (true, predicate, headersDoesNotContainFieldWithNameError(name, sessionHeadersValue))
      }
    )

    def isAbsent = AssertStep(
      title = s"headers do not contain field with name '$name'",
      action = s ⇒
      CustomMessageEqualityAssertion.fromSession(s, headersSessionKey) { (session, sessionHeaders) ⇒
        val sessionHeadersValue = HttpService.decodeSessionHeaders(sessionHeaders)
        val predicate = !sessionHeadersValue.exists { case (hname, _) ⇒ hname == name }
        (true, predicate, headersContainFieldWithNameError(name, sessionHeadersValue))
      }
    )
  }

  def headersDoesNotContainError(expected: String, sourceArray: String): Boolean ⇒ String = resFalse ⇒ {
    val prettyHeaders = displayStringPairs(decodeSessionHeaders(sourceArray))
    s"""expected headers to contain '$expected' but it is not the case with headers:
       |$prettyHeaders""".stripMargin
  }

  def headersDoesNotContainFieldWithNameError(name: String, sourceHeaders: Seq[(String, String)]): Boolean ⇒ String = resFalse ⇒ {
    val prettyHeaders = displayStringPairs(sourceHeaders)
    s"""expected headers to contain field with name '$name' but it is not the case with headers:
       |$prettyHeaders""".stripMargin
  }

  def headersContainFieldWithNameError(name: String, sourceHeaders: Seq[(String, String)]): Boolean ⇒ String = resFalse ⇒ {
    val prettyHeaders = displayStringPairs(sourceHeaders)
    s"""expected headers to not contain field with name '$name' but it is not the case with headers:
       |$prettyHeaders""".stripMargin
  }
}
