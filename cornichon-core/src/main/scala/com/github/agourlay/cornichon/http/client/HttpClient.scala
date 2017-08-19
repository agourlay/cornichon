package com.github.agourlay.cornichon.http.client

import cats.data.EitherT
import com.github.agourlay.cornichon.core.{ CornichonError, Done }
import com.github.agourlay.cornichon.http.{ CornichonHttpResponse, HttpRequest, HttpStreamedRequest }
import io.circe.Json

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait HttpClient {

  def runRequest(req: HttpRequest[Json], t: FiniteDuration): EitherT[Future, CornichonError, CornichonHttpResponse]

  def openStream(req: HttpStreamedRequest, t: FiniteDuration): Future[Either[CornichonError, CornichonHttpResponse]]

  def shutdown(): Future[Done]

  def paramsFromUrl(url: String): Seq[(String, String)]
}