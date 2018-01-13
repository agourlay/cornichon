package com.github.agourlay.cornichon.http.client

import cats.data.EitherT

import com.github.agourlay.cornichon.core.{ CornichonError, Done }
import com.github.agourlay.cornichon.http.{ CornichonHttpResponse, HttpRequest, HttpStreamedRequest }

import io.circe.Json

import monix.eval.Task

import scala.concurrent.duration.FiniteDuration

trait HttpClient {

  def runRequest(req: HttpRequest[Json], t: FiniteDuration): EitherT[Task, CornichonError, CornichonHttpResponse]

  def openStream(req: HttpStreamedRequest, t: FiniteDuration): Task[Either[CornichonError, CornichonHttpResponse]]

  def shutdown(): Task[Done]

  def paramsFromUrl(url: String): Either[CornichonError, Seq[(String, String)]]
}