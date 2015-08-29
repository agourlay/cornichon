package com.github.agourlay.cornichon.http

import akka.http.scaladsl.model.StatusCode
import com.github.agourlay.cornichon.core.CornichonError
import spray.json.JsValue

sealed trait HttpError extends CornichonError

case class StatusError(expected: StatusCode, actual: StatusCode, body: JsValue) extends HttpError {
  val msg = s"Unexpected status code. Expected $expected. Actual: $actual, $body"
}

case class JsonError(e: Exception) extends HttpError {
  val msg = s"Expected Json but got ${e.printStackTrace()}"
}

case class SseError(e: Exception) extends HttpError {
  val msg = s"Expected SSE connection but got ${e.printStackTrace()}"
}

case class TimeoutError(msg: String) extends HttpError