package com.github.agourlay.cornichon.http.client

import akka.http.scaladsl.model.HttpHeader
import cats.data.Xor
import com.github.agourlay.cornichon.http.{ CornichonHttpResponse, HttpError, HttpMethod, HttpStream }
import io.circe.Json

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait HttpClient {

  def runRequest(method: HttpMethod, url: String, payload: Option[Json], params: Seq[(String, String)], headers: Seq[HttpHeader], timeout: FiniteDuration): Xor[HttpError, CornichonHttpResponse]

  def openStream(stream: HttpStream, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], takeWithin: FiniteDuration): Xor[HttpError, CornichonHttpResponse]

  def shutdown(): Future[Unit]
}