package com.github.agourlay.cornichon.http

import akka.http.scaladsl.model.{ HttpResponse, StatusCode }
import com.github.agourlay.cornichon.core.CornichonError

import scala.util.control.NoStackTrace

sealed trait HttpError extends CornichonError with NoStackTrace

case class ResponseError(e: Exception, response: HttpResponse) extends HttpError {
  val msg = s"HTTP response '$response' generated error ${CornichonError.genStacktrace(e)}"
}

case class SseError(e: Exception) extends HttpError {
  val msg = s"expected SSE connection but got ${CornichonError.genStacktrace(e)}"
}

case class TimeoutError(msg: String) extends HttpError