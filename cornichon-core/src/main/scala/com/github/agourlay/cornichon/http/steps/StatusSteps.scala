package com.github.agourlay.cornichon.http.steps

import com.github.agourlay.cornichon.core.Session
import com.github.agourlay.cornichon.http.HttpService.SessionKeys._
import com.github.agourlay.cornichon.http.{ HttpService, StatusNonExpected }
import com.github.agourlay.cornichon.steps.regular.assertStep._

object StatusSteps {

  case object StatusKind {

    protected val statusKind = Map(
      1 -> "informational",
      2 -> "success",
      3 -> "redirection",
      4 -> "client error",
      5 -> "server error"
    )

    def computeKind(status: Int) = status / 100

    def kindDisplay(status: Int) = s"${status}xx"

    def kindLabel(status: Int) = statusKind.getOrElse(status, "unknown")

  }

  case object StatusStepBuilder {
    def is(expected: Int) = AssertStep(
      title = s"status is '$expected'",
      action = sc => Assertion.either {
        sc.session.get(lastResponseStatusKey).map { lastResponseStatus =>
          CustomMessageEqualityAssertion(expected, lastResponseStatus.toInt, () => statusError(expected, lastResponseStatus, sc.session))
        }
      }
    )

    private def isByKind(expectedKind: Int) = AssertStep(
      title = s"status is ${StatusKind.kindLabel(expectedKind)} '${StatusKind.kindDisplay(expectedKind)}'",
      action = sc => Assertion.either {
        sc.session.get(lastResponseStatusKey).map { lastResponseStatus =>
          val actualKind = StatusKind.computeKind(lastResponseStatus.toInt)
          CustomMessageEqualityAssertion(expectedKind, actualKind, () => statusKindError(expectedKind, lastResponseStatus, sc.session))
        }
      }
    )

    def isSuccess: AssertStep = isByKind(2)
    def isRedirect: AssertStep = isByKind(3)
    def isClientError: AssertStep = isByKind(4)
    def isServerError: AssertStep = isByKind(5)
  }

  def statusError(expected: Int, actual: String, session: Session): String = {
    val responseBody = session.get(lastResponseBodyKey).valueUnsafe
    val headers = session.get(lastResponseHeadersKey).flatMap(HttpService.decodeSessionHeaders).valueUnsafe
    val requestDescription = session.get(lastResponseRequestKey).valueUnsafe
    StatusNonExpected(expected, actual.toInt, headers, responseBody, requestDescription).baseErrorMessage
  }

  def statusKindError(expectedKind: Int, actualStatus: String, session: Session): String = {
    val expected = StatusKind.kindDisplay(expectedKind)
    val body = session.get(lastResponseBodyKey).valueUnsafe
    val headers = session.get(lastResponseHeadersKey).flatMap(HttpService.decodeSessionHeaders).valueUnsafe
    val requestDescription = session.get(lastResponseRequestKey).valueUnsafe
    StatusNonExpected(expected, actualStatus, headers, body, requestDescription).baseErrorMessage
  }

}
