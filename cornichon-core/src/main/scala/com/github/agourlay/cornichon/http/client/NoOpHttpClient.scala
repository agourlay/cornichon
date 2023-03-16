package com.github.agourlay.cornichon.http.client

import cats.Show
import cats.data.EitherT
import cats.syntax.either._
import cats.effect.IO
import com.github.agourlay.cornichon.core.Done
import com.github.agourlay.cornichon.http.{ HttpResponse, HttpRequest, HttpStreamedRequest }
import org.http4s.EntityEncoder

import scala.concurrent.duration.FiniteDuration

class NoOpHttpClient extends HttpClient {

  def runRequest[A](cReq: HttpRequest[A], t: FiniteDuration)(implicit ee: EntityEncoder[IO, A], sh: Show[A]) =
    EitherT.apply(IO.pure(HttpResponse(200, Nil, "NoOpBody").asRight))

  def openStream(req: HttpStreamedRequest, t: FiniteDuration) =
    IO.pure(HttpResponse(200, Nil, "NoOpBody").asRight)

  def shutdown() =
    Done.ioDone

  def paramsFromUrl(url: String) =
    Right(Nil)
}