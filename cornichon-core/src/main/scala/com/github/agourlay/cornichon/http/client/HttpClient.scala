package com.github.agourlay.cornichon.http.client

import cats.Show
import cats.data.EitherT
import com.github.agourlay.cornichon.core.{ CornichonError, Done }
import com.github.agourlay.cornichon.http.{ DslHttpStreamedRequest, HttpRequest, HttpResponse }
import monix.eval.Task
import org.http4s.{ EntityEncoder, Request }

import scala.concurrent.duration.FiniteDuration

trait HttpClient {

  def runRequest[A: Show](cReq: HttpRequest[A], t: FiniteDuration)(ee: EntityEncoder[Task, A]): EitherT[Task, CornichonError, (Request[Task], HttpResponse)]

  def openStream(req: DslHttpStreamedRequest, t: FiniteDuration): Task[Either[CornichonError, HttpResponse]]

  def shutdown(): Task[Done]

  def paramsFromUrl(url: String): Either[CornichonError, Seq[(String, String)]]
}