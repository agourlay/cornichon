package com.github.agourlay.cornichon.http

import java.util.concurrent.TimeoutException

import cats.Show
import cats.syntax.show._
import com.github.agourlay.cornichon.core.CornichonError

import scala.util.control.NoStackTrace

sealed trait HttpError extends CornichonError with NoStackTrace

case class TimeoutError[A: Show](request: A, error: TimeoutException) extends HttpError {
  val msg =
    s"""|${request.show}
        |failed with a timeout error - ${error.getMessage}
     """.stripMargin
}

case class RequestError[A: Show](request: A, e: Throwable) extends HttpError {
  val msg =
    s"""|${request.show}
        |failed with error:
        |${CornichonError.genStacktrace(e)}
     """.stripMargin
}

case class UnmarshallingResponseError(e: Throwable, response: String) extends HttpError {
  val msg = s"HTTP response '$response' generated error ${CornichonError.genStacktrace(e)}"
}

case class SseError(e: Throwable) extends HttpError {
  val msg = s"expected SSE connection but got ${CornichonError.genStacktrace(e)}"
}

case class WsUpgradeError(status: Int) extends HttpError {
  val msg = s"Websocket upgrade error - status received '$status'"
}

case class StatusNonExpected(expected: Int, response: CornichonHttpResponse) extends HttpError {
  val msg =
    s"""status code expected was '$expected' but '${response.status}' was received with body:
       | ${response.body}""".stripMargin
}

case class MalformedHeadersError(error: String) extends CornichonError {
  val msg = s"parsing headers generated error '$error'"
}