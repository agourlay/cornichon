package com.github.agourlay.cornichon.http.steps

import com.github.agourlay.cornichon.core.Session
import com.github.agourlay.cornichon.http.HttpService.SessionKeys._
import com.github.agourlay.cornichon.http.{ HttpService, StatusNonExpected }
import com.github.agourlay.cornichon.steps.regular.assertStep._

object StatusSteps {

  private case object StatusKind {

    def computeKind(status: Short): Short = (status / 100).toShort

    def kindDisplay(status: Short) = s"${status}xx"

    def kindLabel(status: Short): String = status match {
      case 1 => "informational"
      case 2 => "success"
      case 3 => "redirection"
      case 4 => "client error"
      case 5 => "server error"
      case _ => "unknown"
    }
  }

  case object StatusStepBuilder {
    def is(expected: Short): AssertStep = AssertStep(
      title = s"status is '$expected'",
      action = sc => Assertion.either {
        sc.session.get(lastResponseStatusKey).map { lastResponseStatus =>
          CustomMessageEqualityAssertion(expected, lastResponseStatus.toShort, () => statusError(expected, lastResponseStatus, sc.session))
        }
      }
    )

    private def isByKind(expectedKind: Short) = AssertStep(
      title = s"status is ${StatusKind.kindLabel(expectedKind)} '${StatusKind.kindDisplay(expectedKind)}'",
      action = sc => Assertion.either {
        sc.session.get(lastResponseStatusKey).map { lastResponseStatus =>
          val actualKind = StatusKind.computeKind(lastResponseStatus.toShort)
          CustomMessageEqualityAssertion(expectedKind, actualKind, () => statusKindError(expectedKind, lastResponseStatus, sc.session))
        }
      }
    )

    def isSuccess: AssertStep = isByKind(2)
    def isRedirect: AssertStep = isByKind(3)
    def isClientError: AssertStep = isByKind(4)
    def isServerError: AssertStep = isByKind(5)
  }

  private def statusError(expected: Short, actual: String, session: Session): String = {
    val responseBody = session.get(lastResponseBodyKey).valueUnsafe
    val headers = session.get(lastResponseHeadersKey).flatMap(HttpService.decodeSessionHeaders).valueUnsafe
    val requestDescription = session.get(lastResponseRequestKey).valueUnsafe
    StatusNonExpected(expected, actual.toShort, headers, responseBody, requestDescription).baseErrorMessage
  }

  private def statusKindError(expectedKind: Short, actualStatus: String, session: Session): String = {
    val expected = StatusKind.kindDisplay(expectedKind)
    val body = session.get(lastResponseBodyKey).valueUnsafe
    val headers = session.get(lastResponseHeadersKey).flatMap(HttpService.decodeSessionHeaders).valueUnsafe
    val requestDescription = session.get(lastResponseRequestKey).valueUnsafe
    StatusNonExpected(expected, actualStatus, headers, body, requestDescription).baseErrorMessage
  }

}
