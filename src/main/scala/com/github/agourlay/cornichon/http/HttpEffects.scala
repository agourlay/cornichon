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
      base + paramsTitle + headersTitle
    }

    def paramsTitle = if (params.isEmpty) "" else s" with params ${displayTuples(params)}"
    def headersTitle = if (headers.isEmpty) "" else s" with headers ${displayTuples(headers)}"
  }

  case class Get(url: String, params: Seq[(String, String)], headers: Seq[(String, String)]) extends HttpRequest {
    val name = "GET"

    def withParams(params: (String, String)*) = copy(params = params)
    def withHeaders(headers: (String, String)*) = copy(headers = headers)
  }

  case class Delete(url: String, params: Seq[(String, String)], headers: Seq[(String, String)]) extends HttpRequest {
    val name = "DELETE"

    def withParams(params: (String, String)*) = copy(params = params)
    def withHeaders(headers: (String, String)*) = copy(headers = headers)

  }

  sealed trait HttpRequestWithPayload extends HttpRequest {
    val payload: String

    override def description: String = {
      val base = s"$name to $url"
      val payloadTitle = if (payload.isEmpty) " without payload" else s" with payload $payload"
      base + payloadTitle + paramsTitle + headersTitle
    }
  }

  case class Post(url: String, payload: String, params: Seq[(String, String)], headers: Seq[(String, String)]) extends HttpRequestWithPayload {
    val name = "POST"
    def withParams(params: (String, String)*) = copy(params = params)
    def withHeaders(headers: (String, String)*) = copy(headers = headers)
  }

  case class Put(url: String, payload: String, params: Seq[(String, String)], headers: Seq[(String, String)]) extends HttpRequestWithPayload {
    val name = "PUT"
    def withParams(params: (String, String)*) = copy(params = params)
    def withHeaders(headers: (String, String)*) = copy(headers = headers)
  }

  sealed trait HttpRequestStreamed extends HttpRequest {
    val takeWithin: FiniteDuration
  }

  case class OpenSSE(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)]) extends HttpRequestStreamed {
    val name = "Open SSE"
    def withParams(params: (String, String)*) = copy(params = params)
    def withHeaders(headers: (String, String)*) = copy(headers = headers)
  }

  case class OpenWS(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)]) extends HttpRequestStreamed {
    val name = "Open WS"
    def withParams(params: (String, String)*) = copy(params = params)
    def withHeaders(headers: (String, String)*) = copy(headers = headers)
  }

}
