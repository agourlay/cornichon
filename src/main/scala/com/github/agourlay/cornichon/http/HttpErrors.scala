package com.github.agourlay.cornichon.http

import akka.http.scaladsl.model.{ HttpResponse, StatusCode }
import com.github.agourlay.cornichon.core.CornichonError

sealed trait HttpError extends CornichonError

case class StatusError(expected: Int, actual: Int, body: String) extends HttpError {
  val msg =
    s"""expected '$expected' but actual is '$actual' with response body:
       |$body """.stripMargin
}

case class ResponseError(e: Exception, response: HttpResponse) extends HttpError {
  val msg = s"HTTP response '$response' generated error ${e.printStackTrace()}"
}

case class SseError(e: Exception) extends HttpError {
  val msg = s"expected SSE connection but got ${e.printStackTrace()}"
}

case class TimeoutError(msg: String) extends HttpError