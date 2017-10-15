package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.core.Done
import com.github.agourlay.cornichon.http.client.HttpClient
import io.circe.Json

import scala.concurrent.duration.FiniteDuration

class NoOpHttpClient extends HttpClient {

  def runRequest(req: HttpRequest[Json], t: FiniteDuration) = ???

  def openStream(req: HttpStreamedRequest, t: FiniteDuration) = ???

  def shutdown() = Done.futureDone

  def paramsFromUrl(url: String) = Right(Nil)
}