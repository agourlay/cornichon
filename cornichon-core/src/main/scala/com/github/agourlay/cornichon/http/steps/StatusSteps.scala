package com.github.agourlay.cornichon.http.steps

import cats.syntax.show._
import cats.syntax.either._
import cats.instances.int._
import cats.instances.string._

import com.github.agourlay.cornichon.http.HttpService.SessionKeys._
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.steps.regular.assertStep._

object StatusSteps {

  case object StatusKind {

    protected val statusKind = Map(
      1 → "informational",
      2 → "success",
      3 → "redirection",
      4 → "client error",
      5 → "server error"
    )

    def computeKind(status: Int) = status / 100

    def kindDisplay(status: Int) = s"'${status}xx'"

    def kindLabel(status: Int) = statusKind.getOrElse(status, "unknown")

  }

  case object StatusStepBuilder {
    def is(expected: Int) = AssertStep(
      title = s"status is '$expected'",
      action = s ⇒ Assertion.either {
      for {
        lastResponseStatus ← s.get(lastResponseStatusKey).map(_.toInt)
        lastBody ← s.get(lastResponseBodyKey)
      } yield CustomMessageEqualityAssertion(expected, lastResponseStatus, statusError(expected, lastBody))
    }
    )

    protected def isByKind(kind: Int) = AssertStep(
      title = s"status is ${StatusKind.kindLabel(kind)} ${StatusKind.kindDisplay(kind)}",
      action = s ⇒ Assertion.either {
      for {
        lastResponseStatus ← s.get(lastResponseStatusKey).map(_.toInt)
        lastBody ← s.get(lastResponseBodyKey)
      } yield CustomMessageEqualityAssertion(
        expected = kind,
        actual = StatusKind.computeKind(lastResponseStatus),
        customMessage = statusKindError(kind, lastResponseStatus, lastBody)
      )
    }
    )

    def isSuccess = isByKind(2)
    def isRedirect = isByKind(3)
    def isClientError = isByKind(4)
    def isServerError = isByKind(5)

  }

  // TODO do not assume that body is JSON - use content-type
  def withResponseBody(body: String) = s""" with response body:
       |${parseJsonUnsafe(body).show}""".stripMargin

  def statusError(expected: Int, body: String): Int ⇒ String = actual ⇒ {
    s"expected status '$expected' but actual is '$actual' ${withResponseBody(body)}"
  }

  def statusKindError(expectedKind: Int, actualStatus: Int, body: String): Int ⇒ String = actual ⇒ {
    val kindShow = StatusKind.kindDisplay(expectedKind)
    val label = StatusKind.kindLabel(expectedKind)
    val actualLabel = StatusKind.kindLabel(actual)
    s"expected a $label $kindShow status but actual is $actualLabel '$actualStatus' ${withResponseBody(body)}"
  }

}
