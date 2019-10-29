package com.github.agourlay.cornichon.http.steps

import cats.instances.tuple._
import cats.instances.boolean._
import cats.instances.string._

import com.github.agourlay.cornichon.http.HttpService
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.http.HttpService.SessionKeys._
import com.github.agourlay.cornichon.steps.regular.assertStep._
import com.github.agourlay.cornichon.util.Printing._

import scala.collection.breakOut

// The assertion are case-insensitive on the field names.
// https://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2
object HeadersSteps {

  case object HeadersStepBuilder {
    def is(expected: (String, String)*) = AssertStep(
      title = s"headers is ${printArrowPairs(expected)}",
      action = sc => Assertion.either {
        for {
          sessionHeaders <- sc.session.get(lastResponseHeadersKey)
          sessionHeadersValue <- decodeSessionHeaders(sessionHeaders)
          lowerCasedActual = sessionHeadersValue.map { case (name, value) => name.toLowerCase -> value }
          lowerCasedExpected: List[(String, String)] = expected.map { case (name, value) => name.toLowerCase -> value }(breakOut)
        } yield CollectionsContainSameElements(lowerCasedExpected, lowerCasedActual)
      }
    )

    def hasSize(expectedSize: Int) = AssertStep(
      title = s"headers size is '$expectedSize'",
      action = sc => Assertion.either {
        sc.session.get(lastResponseHeadersKey).map { sessionHeaders =>
          CollectionSizeAssertion(sessionHeaders.split(interHeadersValueDelim), expectedSize, "headers")
        }
      }
    )

    def contain(elements: (String, String)*) = AssertStep(
      title = s"headers contain ${printArrowPairs(elements)}",
      action = sc => Assertion.either {
        for {
          sessionHeaders <- sc.session.get(lastResponseHeadersKey)
          sessionHeadersValue <- decodeSessionHeaders(sessionHeaders)
          lowerCasedActual = sessionHeadersValue.map { case (name, value) => name.toLowerCase -> value }
          predicate = elements.forall { case (name, value) => lowerCasedActual.contains(name.toLowerCase -> value) }
        } yield CustomMessageEqualityAssertion(true, predicate, () => headersDoesNotContainError(printArrowPairs(elements), sessionHeaders))
      }
    )

    def name(name: String) = HeadersNameStepBuilder(name)
  }

  case class HeadersNameStepBuilder(name: String) {
    def isPresent = AssertStep(
      title = s"headers contain field with name '$name'",
      action = sc => Assertion.either {
        for {
          sessionHeaders <- sc.session.get(lastResponseHeadersKey)
          sessionHeadersValue <- HttpService.decodeSessionHeaders(sessionHeaders)
          predicate <- Right(sessionHeadersValue.exists { case (hname, _) => hname.toLowerCase == name.toLowerCase })
        } yield CustomMessageEqualityAssertion(true, predicate, () => headersDoesNotContainFieldWithNameError(name, sessionHeadersValue))
      }
    )

    def isAbsent = AssertStep(
      title = s"headers do not contain field with name '$name'",
      action = sc => Assertion.either {
        for {
          sessionHeaders <- sc.session.get(lastResponseHeadersKey)
          sessionHeadersValue <- HttpService.decodeSessionHeaders(sessionHeaders)
          predicate <- Right(!sessionHeadersValue.exists { case (hName, _) => hName.toLowerCase == name.toLowerCase })
        } yield CustomMessageEqualityAssertion(true, predicate, () => headersContainFieldWithNameError(name, sessionHeadersValue))
      }
    )
  }

  def headersDoesNotContainError(expected: String, sourceArray: String): String = {
    val prettyHeaders = printArrowPairs(decodeSessionHeaders(sourceArray).valueUnsafe)
    s"""expected headers to contain '$expected' but it is not the case with headers:
       |$prettyHeaders""".stripMargin
  }

  def headersDoesNotContainFieldWithNameError(name: String, sourceHeaders: Seq[(String, String)]): String = {
    val prettyHeaders = printArrowPairs(sourceHeaders)
    s"""expected headers to contain field with name '$name' but it is not the case with headers:
       |$prettyHeaders""".stripMargin
  }

  def headersContainFieldWithNameError(name: String, sourceHeaders: Seq[(String, String)]): String = {
    val prettyHeaders = printArrowPairs(sourceHeaders)
    s"""expected headers to not contain field with name '$name' but it is not the case with headers:
       |$prettyHeaders""".stripMargin
  }
}
