package com.github.agourlay.cornichon.http

import akka.http.scaladsl.model._

import scala.collection.immutable

case class CornichonHttpResponse(status: StatusCode, headers: immutable.Seq[HttpHeader] = Nil, body: String)

object CornichonHttpResponse {
  def fromResponse(resp: HttpResponse, body: String) = CornichonHttpResponse(resp.status, resp.headers, body)
}