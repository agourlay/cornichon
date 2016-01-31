package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.dsl.Dsl._

import scala.concurrent.duration.FiniteDuration

object HttpEffects {

  sealed trait HttpRequest {
    val name: String
    val url: String

    val params: Seq[(String, String)]
    def withParams(params: (String, String)*): HttpRequest

    val headers: Seq[(String, String)]
    def withHeaders(params: (String, String)*): HttpRequest

    def description: String = {
      val base = s"$name $url"
      if (params.isEmpty) base
      else s"$base with params ${displayTuples(params)}"
    }
  }

  case class Get(url: String, params: Seq[(String, String)], headers: Seq[(String, String)]) extends HttpRequest {
    val name = "Get"

    def withParams(params: (String, String)*) = copy(params = params)
    def withHeaders(headers: (String, String)*) = copy(headers = headers)
  }

  case class Delete(url: String, params: Seq[(String, String)], headers: Seq[(String, String)]) extends HttpRequest {
    val name = "Delete"

    def withParams(params: (String, String)*) = copy(params = params)
    def withHeaders(headers: (String, String)*) = copy(headers = headers)

  }

  sealed trait HttpRequestWithPayload extends HttpRequest {
    val payload: String

    override def description: String = {
      val base = s"$name to $url with payload $payload"
      if (params.isEmpty) base
      else s"$base with params ${displayTuples(params)}"
    }
  }

  case class Post(url: String, payload: String, params: Seq[(String, String)], headers: Seq[(String, String)]) extends HttpRequestWithPayload {
    val name = "Post"
    def withParams(params: (String, String)*) = copy(params = params)
    def withHeaders(headers: (String, String)*) = copy(headers = headers)
  }

  case class Put(url: String, payload: String, params: Seq[(String, String)], headers: Seq[(String, String)]) extends HttpRequestWithPayload {
    val name = "Put"
    def withParams(params: (String, String)*) = copy(params = params)
    def withHeaders(headers: (String, String)*) = copy(headers = headers)
  }

  sealed trait HttpRequestStreamed extends HttpRequest {
    val takeWithin: FiniteDuration
  }

  case class GetSSE(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)]) extends HttpRequestStreamed {
    val name = "Get SSE"
    def withParams(params: (String, String)*) = copy(params = params)
    def withHeaders(headers: (String, String)*) = copy(headers = headers)
  }

  case class GetWS(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)]) extends HttpRequestStreamed {
    val name = "Get WS"
    def withParams(params: (String, String)*) = copy(params = params)
    def withHeaders(headers: (String, String)*) = copy(headers = headers)
  }

}
