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

    //TODO
    // isSuccess
    // isRedirection
    // isClientError
    // isServerError
  }

  // TODO do not assume that body is JSON - use content-type
  def statusError(expected: Int, body: String): Int ⇒ String = actual ⇒ {
    s"""expected '$expected' but actual is '$actual' with response body:
       |${parseJsonUnsafe(body).show}""".stripMargin
  }

}
