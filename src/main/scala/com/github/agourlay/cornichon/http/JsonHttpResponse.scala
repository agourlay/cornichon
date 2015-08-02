package com.github.agourlay.cornichon.http

import akka.http.scaladsl.model._
import spray.json.JsValue

import scala.collection.immutable

case class JsonHttpResponse(status: StatusCode, headers: immutable.Seq[HttpHeader] = Nil, body: JsValue)

object JsonHttpResponse {
  def fromResponse(resp: HttpResponse, body: JsValue) = JsonHttpResponse(resp.status, resp.headers, body)
}