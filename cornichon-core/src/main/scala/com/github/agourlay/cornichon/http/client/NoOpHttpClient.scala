package com.github.agourlay.cornichon.http.client

import cats.Show
import cats.data.EitherT
import cats.syntax.either._
import com.github.agourlay.cornichon.core.Done
import com.github.agourlay.cornichon.http.{ DslHttpStreamedRequest, HttpRequest, HttpResponse }
import monix.eval.Task
import org.http4s.{ EntityEncoder, Request }

import scala.concurrent.duration.FiniteDuration

class NoOpHttpClient extends HttpClient {

  def runRequest[A: Show](cReq: HttpRequest[A], t: FiniteDuration)(ee: EntityEncoder[Task, A]) =
    EitherT.apply(
      Task.now(
        (Request[Task](), HttpResponse(200, Nil, "NoOpBody")).asRight
      )
    )

  def openStream(req: DslHttpStreamedRequest, t: FiniteDuration) =
    Task.now(HttpResponse(200, Nil, "NoOpBody").asRight)

  def shutdown() =
    Done.taskDone

  def paramsFromUrl(url: String) =
    Right(Nil)
}