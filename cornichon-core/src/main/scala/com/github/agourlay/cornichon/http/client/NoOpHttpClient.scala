package com.github.agourlay.cornichon.http.client

import cats.data.EitherT
import cats.syntax.either._
import com.github.agourlay.cornichon.core.Done
import com.github.agourlay.cornichon.http.{ CornichonHttpResponse, HttpRequest, HttpStreamedRequest }
import io.circe.Json
import monix.eval.Task

import scala.concurrent.duration.FiniteDuration

class NoOpHttpClient extends HttpClient {

  def runRequest(req: HttpRequest[Json], t: FiniteDuration) =
    EitherT.apply(Task.now(CornichonHttpResponse(200, Nil, "NoOpBody").asRight))

  def openStream(req: HttpStreamedRequest, t: FiniteDuration) =
    Task.now(CornichonHttpResponse(200, Nil, "NoOpBody").asRight)

  def shutdown() =
    Done.taskDone

  def paramsFromUrl(url: String) =
    Right(Nil)
}