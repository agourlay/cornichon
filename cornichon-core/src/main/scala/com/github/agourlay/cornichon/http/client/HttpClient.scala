package com.github.agourlay.cornichon.http.client

import cats.Show
import cats.data.EitherT
import com.github.agourlay.cornichon.core.{ CornichonError, Done }
import com.github.agourlay.cornichon.http.{ HttpRequest, HttpResponse, HttpStreamedRequest }
import monix.eval.Task
import sttp.client3.BodySerializer

import scala.concurrent.duration.FiniteDuration

trait HttpClient {

  def runRequest[A: Show](cReq: HttpRequest[A], t: FiniteDuration)(implicit bodySer: BodySerializer[A]): EitherT[Task, CornichonError, HttpResponse]

  def openStream(req: HttpStreamedRequest, t: FiniteDuration): Task[Either[CornichonError, HttpResponse]]

  def shutdown(): Task[Done]

  def paramsFromUrl(url: String): Either[CornichonError, Seq[(String, String)]]
}