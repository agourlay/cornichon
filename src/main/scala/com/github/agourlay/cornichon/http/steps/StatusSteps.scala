package com.github.agourlay.cornichon.http.steps

import cats.syntax.show._
import com.github.agourlay.cornichon.http.HttpService.SessionKeys._
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.steps.regular.assertStep._
import com.github.agourlay.cornichon.util.Instances._

object StatusSteps {

  case object StatusStepBuilder {
    def is(expected: Int) = AssertStep(
      title = s"status is '$expected'",
      action = s ⇒
      CustomMessageEqualityAssertion(
        expected = expected,
        actual = s.get(lastResponseStatusKey).toInt,
        customMessage = statusError(expected, s.get(lastResponseBodyKey))
      )
    )

    protected def isByKind(kind: Int, label: String) = AssertStep(
      title = s"status is $label ${kindDisplay(kind)}",
      action = s ⇒
      CustomMessageEqualityAssertion(
        expected = kind,
        actual = s.get(lastResponseStatusKey).toInt / 100,
        customMessage = statusKindError(kindDisplay(kind), label, s.get(lastResponseBodyKey))
      )
    )

    def isSuccess = isByKind(2, "success")
    def isRedirect = isByKind(3, "redirection")
    def isClientError = isByKind(4, "client error")
    def isServerError = isByKind(5, "server error")

  }

  def kindDisplay(status: Int) = s"'${status}xx'"

  // TODO do not assume that body is JSON - use content-type
  def withResponseBody(body: String) = s""" with response body:
       |${parseJsonUnsafe(body).show}""".stripMargin

  def statusError(expected: Int, body: String): Int ⇒ String = actual ⇒ {
    s"expected status '$expected' but actual is '$actual' ${withResponseBody(body)}"
  }

  def statusKindError(kind: String, label: String, body: String): Int ⇒ String = actual ⇒ {
    s"expected a $label $kind status but actual is ${kindDisplay(actual)} ${withResponseBody(body)}"
  }

}
