package com.github.agourlay.cornichon.http.steps

import cats.instances.list._
import cats.instances.boolean._
import cats.instances.string._

import com.github.agourlay.cornichon.core.SessionKey
import com.github.agourlay.cornichon.http.HttpService
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.http.HttpService.SessionKeys._
import com.github.agourlay.cornichon.steps.regular.assertStep._
import com.github.agourlay.cornichon.util.Printing._

object HeadersSteps {
  private val headersSessionKey = SessionKey(lastResponseHeadersKey)

  case class HeadersStepBuilder(private val ordered: Boolean) {
    def is(expected: (String, String)*) = AssertStep(
      title = s"headers is ${printArrowPairs(expected)}",
      action = s ⇒ Assertion.either {
        s.get(headersSessionKey).map { sessionHeaders ⇒
          val actualValue = sessionHeaders.split(",").toList
          val expectedValue = expected.toList.map { case (name, value) ⇒ s"$name$headersKeyValueDelim$value" }
          GenericEqualityAssertion(expectedValue, actualValue)
        }
      }
    )

    def hasSize(expectedSize: Int) = AssertStep(
      title = s"headers size is '$expectedSize'",
      action = s ⇒ Assertion.either {
        s.get(headersSessionKey).map { sessionHeaders ⇒
          CollectionSizeAssertion(sessionHeaders.split(","), expectedSize).withName("headers")
        }
      }
    )

    def contain(elements: (String, String)*) = AssertStep(
      title = s"headers contain ${printArrowPairs(elements)}",
      action = s ⇒ Assertion.either {
        s.get(headersSessionKey).map { sessionHeaders ⇒
          val sessionHeadersValue = sessionHeaders.split(interHeadersValueDelim)
          val predicate = elements.forall { case (name, value) ⇒ sessionHeadersValue.contains(s"$name$headersKeyValueDelim$value") }
          CustomMessageEqualityAssertion(true, predicate, headersDoesNotContainError(printArrowPairs(elements), sessionHeaders))
        }
      }
    )

    def inOrder: HeadersStepBuilder = copy(ordered = true)

    def name(name: String) = HeadersNameStepBuilder(name)
  }

  case class HeadersNameStepBuilder(name: String) {
    def isPresent = AssertStep(
      title = s"headers contain field with name '$name'",
      action = s ⇒ Assertion.either {
        for {
          sessionHeaders ← s.get(headersSessionKey)
          sessionHeadersValue ← HttpService.decodeSessionHeaders(sessionHeaders)
          predicate ← Right(sessionHeadersValue.exists { case (hname, _) ⇒ hname == name })
        } yield CustomMessageEqualityAssertion(true, predicate, headersDoesNotContainFieldWithNameError(name, sessionHeadersValue))
      }
    )

    def isAbsent = AssertStep(
      title = s"headers do not contain field with name '$name'",
      action = s ⇒ Assertion.either {
        for {
          sessionHeaders ← s.get(headersSessionKey)
          sessionHeadersValue ← HttpService.decodeSessionHeaders(sessionHeaders)
          predicate ← Right(!sessionHeadersValue.exists { case (hname, _) ⇒ hname == name })
        } yield CustomMessageEqualityAssertion(true, predicate, headersContainFieldWithNameError(name, sessionHeadersValue))
      }
    )
  }

  def headersDoesNotContainError(expected: String, sourceArray: String): Boolean ⇒ String = resFalse ⇒ {
    val prettyHeaders = printArrowPairs(decodeSessionHeaders(sourceArray).fold(e ⇒ throw e.toException, _.toSeq))
    s"""expected headers to contain '$expected' but it is not the case with headers:
       |$prettyHeaders""".stripMargin
  }

  def headersDoesNotContainFieldWithNameError(name: String, sourceHeaders: Seq[(String, String)]): Boolean ⇒ String = resFalse ⇒ {
    val prettyHeaders = printArrowPairs(sourceHeaders)
    s"""expected headers to contain field with name '$name' but it is not the case with headers:
       |$prettyHeaders""".stripMargin
  }

  def headersContainFieldWithNameError(name: String, sourceHeaders: Seq[(String, String)]): Boolean ⇒ String = resFalse ⇒ {
    val prettyHeaders = printArrowPairs(sourceHeaders)
    s"""expected headers to not contain field with name '$name' but it is not the case with headers:
       |$prettyHeaders""".stripMargin
  }
}
