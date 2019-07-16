package com.github.agourlay.cornichon.http.client

import cats.Show
import cats.data.EitherT
import com.github.agourlay.cornichon.core.{ CornichonError, Done }
import com.github.agourlay.cornichon.http.{ CornichonHttpResponse, HttpRequest, HttpStreamedRequest }
import monix.eval.Task
import org.http4s.EntityEncoder

import scala.concurrent.duration.FiniteDuration

trait HttpClient {

  def runRequest[A: Show](cReq: HttpRequest[A], t: FiniteDuration)(implicit ee: EntityEncoder[Task, A]): EitherT[Task, CornichonError, CornichonHttpResponse]

  def openStream(req: HttpStreamedRequest, t: FiniteDuration): Task[Either[CornichonError, CornichonHttpResponse]]

  def shutdown(): Task[Done]

  def paramsFromUrl(url: String): Either[CornichonError, Seq[(String, String)]]
}