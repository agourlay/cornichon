package com.github.agourlay.cornichon.http

import cats.Show
import cats.syntax.show._
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson.parseDslJsonUnsafe
import com.github.agourlay.cornichon.util.StringUtils.printArrowPairs

import scala.concurrent.duration.FiniteDuration

sealed trait HttpError extends CornichonError

case class TimeoutErrorAfter(request: String, after: FiniteDuration) extends HttpError {
  lazy val baseErrorMessage =
    s"""|$request
        |connection timed out error after ${after.toMillis} ms""".trim.stripMargin
}

case class RequestError(request: String, e: Throwable) extends HttpError {
  lazy val baseErrorMessage =
    s"""|request
        |encountered the following error:
        |${CornichonError.genStacktrace(e)}""".trim.stripMargin
}

case class StatusNonExpected[A: Show](expectedStatus: A, actualStatus: A, headers: Seq[(String, String)], rawBody: String, requestDescription: String) extends HttpError {
  lazy val baseErrorMessage = {
    // TODO do not assume that body is JSON - use content-type
    val prettyBody = parseDslJsonUnsafe(rawBody).show
    val headersMsg = if (headers.isEmpty) "" else s"and with headers:\n${printArrowPairs(headers)}"
    s"""expected status code '${expectedStatus.show}' but '${actualStatus.show}' was received with body:
       |$prettyBody
       |$headersMsg
       |
       |for request
       |
       |$requestDescription""".stripMargin
  }
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