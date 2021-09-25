package com.github.agourlay.cornichon.http.client

import cats.Show
import cats.data.EitherT
import cats.effect.IO
import com.github.agourlay.cornichon.core.{ CornichonError, Done }
import com.github.agourlay.cornichon.http.{ HttpResponse, HttpRequest, HttpStreamedRequest }
import org.http4s.EntityEncoder

import scala.concurrent.duration.FiniteDuration

trait HttpClient {

  def runRequest[A: Show](cReq: HttpRequest[A], t: FiniteDuration)(implicit ee: EntityEncoder[IO, A]): EitherT[IO, CornichonError, HttpResponse]

  def openStream(req: HttpStreamedRequest, t: FiniteDuration): IO[Either[CornichonError, HttpResponse]]

  def shutdown(): IO[Done]

  def paramsFromUrl(url: String): Either[CornichonError, Seq[(String, String)]]
}