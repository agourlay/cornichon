package com.github.agourlay.cornichon.http.client

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.http.{ CornichonHttpResponse, HttpMethod, HttpStream }
import io.circe.Json

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait HttpClient {

  def runRequest(
    method: HttpMethod,
    url: String,
    payload: Option[Json],
    params: Seq[(String, String)],
    headers: Seq[(String, String)]
  ): Future[Either[CornichonError, CornichonHttpResponse]]

  def openStream(
    stream: HttpStream,
    url: String,
    params: Seq[(String, String)],
    headers: Seq[(String, String)],
    takeWithin: FiniteDuration
  ): Future[Either[CornichonError, CornichonHttpResponse]]

  def shutdown(): Future[Unit]

  def paramsFromUrl(url: String): Seq[(String, String)]
}