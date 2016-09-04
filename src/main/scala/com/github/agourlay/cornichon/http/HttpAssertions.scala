package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.dsl.CollectionAssertionSyntax
import com.github.agourlay.cornichon.dsl.Dsl._
import com.github.agourlay.cornichon.http.HttpAssertionErrors._
import com.github.agourlay.cornichon.http.HttpService.SessionKeys._
import com.github.agourlay.cornichon.steps.regular.{ AssertStep, CustomMessageAssertion }
import com.github.agourlay.cornichon.util.Formats._
import com.github.agourlay.cornichon.util.ShowInstances._

object HttpAssertions {

  case object StatusAssertion {
    def is(expected: Int) = AssertStep(
      title = s"status is '$expected'",
      action = s ⇒ CustomMessageAssertion(
      expected = expected,
      actual = s.get(LastResponseStatusKey).toInt,
      customMessage = statusError(expected, s.get(LastResponseBodyKey))
    )
    )
  }

  case class HeadersAssertion(private val ordered: Boolean) extends CollectionAssertionSyntax[(String, String), String] {
    def is(expected: (String, String)*) = from_session_step[Iterable[String]](
      title = s"headers is ${displayTuples(expected)}",
      key = LastResponseHeadersKey,
      expected = s ⇒ expected.map { case (name, value) ⇒ s"$name$HeadersKeyValueDelim$value" },
      mapValue = (session, sessionHeaders) ⇒ sessionHeaders.split(",")
    )

    def hasSize(expected: Int) = from_session_step(
      title = s"headers size is '$expected'",
      key = LastResponseHeadersKey,
      expected = s ⇒ expected,
      mapValue = (session, sessionHeaders) ⇒ sessionHeaders.split(",").length
    )

    def contain(elements: (String, String)*) = {
      from_session_detail_step(
        title = s"headers contain ${displayTuples(elements)}",
        key = LastResponseHeadersKey,
        expected = s ⇒ true,
        mapValue = (session, sessionHeaders) ⇒ {
        val sessionHeadersValue = sessionHeaders.split(InterHeadersValueDelim)
        val predicate = elements.forall { case (name, value) ⇒ sessionHeadersValue.contains(s"$name$HeadersKeyValueDelim$value") }
        (predicate, headersDoesNotContainError(displayTuples(elements), sessionHeaders))
      }
      )
    }

    override def inOrder: HeadersAssertion = copy(ordered = true)
  }
}
