package com.github.agourlay.cornichon.http.client

import cats.Show
import cats.data.EitherT
import cats.syntax.either._
import com.github.agourlay.cornichon.core.Done
import com.github.agourlay.cornichon.http.{ CornichonHttpResponse, HttpRequest, HttpStreamedRequest }
import monix.eval.Task
import org.http4s.EntityEncoder

import scala.concurrent.duration.FiniteDuration

class NoOpHttpClient extends HttpClient {

  def runRequest[A: Show](cReq: HttpRequest[A], t: FiniteDuration)(implicit ee: EntityEncoder[Task, A]) =
    EitherT.apply(Task.now(CornichonHttpResponse(200, Nil, "NoOpBody").asRight))

  def openStream(req: HttpStreamedRequest, t: FiniteDuration) =
    Task.now(CornichonHttpResponse(200, Nil, "NoOpBody").asRight)

  def shutdown() =
    Done.taskDone

  def paramsFromUrl(url: String) =
    Right(Nil)
}