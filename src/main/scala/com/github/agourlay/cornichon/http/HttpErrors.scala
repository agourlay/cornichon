package com.github.agourlay.cornichon.http

import akka.http.scaladsl.model.{ HttpResponse, StatusCode }
import com.github.agourlay.cornichon.core.CornichonError

sealed trait HttpError extends CornichonError

case class RequestError(e: Throwable, request: String) extends HttpError {
  val msg = s"HTTP request '$request' failed with ${e.printStackTrace()}"
}

case class ResponseError(e: Throwable, response: HttpResponse) extends HttpError {
  val msg = s"HTTP response '$response' generated error ${e.printStackTrace()}"
}

case class SseError(e: Throwable) extends HttpError {
  val msg = s"expected SSE connection but got ${e.printStackTrace()}"
}

case class TimeoutError(msg: String) extends HttpError