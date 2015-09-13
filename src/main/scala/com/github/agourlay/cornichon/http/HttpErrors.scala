package com.github.agourlay.cornichon.http

import akka.http.scaladsl.model.{ HttpResponse, StatusCode }
import com.github.agourlay.cornichon.core.CornichonError
import spray.json.JsValue

sealed trait HttpError extends CornichonError

case class StatusError(expected: StatusCode, actual: StatusCode, body: JsValue) extends HttpError {
  val msg = s"Unexpected status code. Expected $expected. Actual: $actual, $body"
}

case class ResponseError(e: Exception, response: HttpResponse) extends HttpError {
  val msg = s"HTTP response '$response' generated error ${e.printStackTrace()}"
}

case class SseError(e: Exception) extends HttpError {
  val msg = s"Expected SSE connection but got ${e.printStackTrace()}"
}

case class TimeoutError(msg: String) extends HttpError