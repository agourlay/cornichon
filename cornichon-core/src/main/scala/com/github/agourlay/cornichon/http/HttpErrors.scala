package com.github.agourlay.cornichon.http

import cats.Show
import cats.syntax.show._
import cats.instances.string._
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson.parseJsonUnsafe

import scala.concurrent.duration.FiniteDuration

sealed trait HttpError extends CornichonError

case class TimeoutErrorAfter[A: Show](request: A, after: FiniteDuration) extends HttpError {
  lazy val baseErrorMessage =
    s"""|${request.show}
        |connection timed out error after ${after.toMillis} ms""".trim.stripMargin
}

case class RequestError[A: Show](request: A, e: Throwable) extends HttpError {
  lazy val baseErrorMessage =
    s"""|${request.show}
        |encountered the following error:
        |${CornichonError.genStacktrace(e)}""".trim.stripMargin
}

case class UnmarshallingResponseError(e: Throwable, response: String) extends HttpError {
  lazy val baseErrorMessage = s"""HTTP response '$response' generated error:
               |${CornichonError.genStacktrace(e)}""".trim.stripMargin
}

case class SseError(e: Throwable) extends HttpError {
  lazy val baseErrorMessage =
    s"""expected SSE connection but got error:
       |${CornichonError.genStacktrace(e)}""".trim.stripMargin
}

case class WsUpgradeError(status: Int) extends HttpError {
  lazy val baseErrorMessage = s"Websocket upgrade error - status received '$status'"
}

//TODO unify with StatusSteps assertion
case class StatusNonExpected(expected: Int, response: CornichonHttpResponse) extends HttpError {
  lazy val baseErrorMessage =
    s"""expected status code '$expected' but '${response.status}' was received with body:
       |${parseJsonUnsafe(response.body).show}""".stripMargin
}

case class MalformedHeadersError(error: String) extends CornichonError {
  lazy val baseErrorMessage =
    s"""parsing headers generated error:
       |$error""".trim.stripMargin
}

case class MalformedUriError(uri: String, error: String) extends CornichonError {
  lazy val baseErrorMessage =
    s"""parsing URI '$uri' generated error:
       |$error""".trim.stripMargin
}

case class BadSessionHeadersEncoding(header: String) extends CornichonError {
  lazy val baseErrorMessage =
    s"""header '$header' does not respect the session encoding convention.
       |Hint: use HttpService.encodeSessionHeaders to properly encode your headers in the Session.""".trim.stripMargin
}