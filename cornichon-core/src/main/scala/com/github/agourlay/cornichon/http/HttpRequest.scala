package com.github.agourlay.cornichon.http

import cats.Show
import cats.syntax.show._
import com.github.agourlay.cornichon.resolver.Resolvable
import com.github.agourlay.cornichon.util.StringUtils.printArrowPairsBuilder
import io.circe.Encoder

import scala.concurrent.duration.FiniteDuration

case class HttpMethod(name: String) extends AnyVal

object HttpMethods {
  val DELETE = HttpMethod("DELETE")
  val GET = HttpMethod("GET")
  val HEAD = HttpMethod("HEAD")
  val OPTIONS = HttpMethod("OPTIONS")
  val PATCH = HttpMethod("PATCH")
  val POST = HttpMethod("POST")
  val PUT = HttpMethod("PUT")
}

trait BaseRequest {
  def url: String
  def params: Seq[(String, String)]
  def headers: Seq[(String, String)]

  // used for step title log display
  def compactDescription: String

  // used for request session storage
  def detailedDescription: String
}

case class HttpRequest[A: Show: Resolvable: Encoder](method: HttpMethod, url: String, body: Option[A], params: Seq[(String, String)], headers: Seq[(String, String)])
  extends BaseRequest {

  def withParams(params: (String, String)*) = copy(params = params)
  def addParams(params: (String, String)*) = copy(params = this.params ++ params)

  def withHeaders(headers: (String, String)*) = copy(headers = headers)
  def addHeaders(headers: (String, String)*) = copy(headers = this.headers ++ headers)

  def withBody[B: Show: Resolvable: Encoder](body: B) = copy(body = Some(body))

  def compactDescription: String = {
    val builder = new StringBuilder()
    builder.append(method.name)
    builder.append(" ")
    builder.append(url)
    body.foreach { p =>
      builder.append(" with body\n")
      builder.append(p.show)
    }
    if (params.nonEmpty) {
      builder.append(" with query parameters ")
      printArrowPairsBuilder(params, builder)
    }

    if (headers.nonEmpty) {
      builder.append(" with headers ")
      printArrowPairsBuilder(headers, builder)
    }
    builder.toString()
  }

  def detailedDescription: String = {
    val builder = new StringBuilder()
    builder.append("HTTP ")

    // method
    builder.append(method.name)

    // URL
    builder.append(" request to ")
    builder.append(url)
    builder.append("\n")

    // parameters
    if (params.isEmpty)
      builder.append("without parameters")
    else {
      builder.append("with parameters ")
      printArrowPairsBuilder(params, builder)
    }
    builder.append("\n")

    // headers
    if (headers.isEmpty)
      builder.append("without headers")
    else {
      builder.append("with headers ")
      printArrowPairsBuilder(headers, builder)
    }
    builder.append("\n")

    // body
    body match {
      case Some(b) =>
        builder.append("with body\n")
        builder.append(b.show)
      case None => builder.append("without body")
    }
    builder.result()
  }
}

trait HttpRequestsDsl {
  import com.github.agourlay.cornichon.http.HttpMethods._

  def get(url: String): HttpRequest[String] = HttpRequest[String](GET, url, None, Nil, Nil)
  def head(url: String): HttpRequest[String] = HttpRequest[String](HEAD, url, None, Nil, Nil)
  def options(url: String): HttpRequest[String] = HttpRequest[String](OPTIONS, url, None, Nil, Nil)
  def delete(url: String): HttpRequest[String] = HttpRequest[String](DELETE, url, None, Nil, Nil)
  def post(url: String): HttpRequest[String] = HttpRequest[String](POST, url, None, Nil, Nil)
  def put(url: String): HttpRequest[String] = HttpRequest[String](PUT, url, None, Nil, Nil)
  def patch(url: String): HttpRequest[String] = HttpRequest[String](PATCH, url, None, Nil, Nil)
}

case class HttpStream(name: String) extends AnyVal

object HttpStreams {
  val SSE = HttpStream("Server-Sent-Event")
  val WS = HttpStream("WebSocket")
}

case class HttpStreamedRequest(stream: HttpStream, url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)])
  extends BaseRequest {

  def withParams(params: (String, String)*) = copy(params = params)
  def addParams(params: (String, String)*) = copy(params = this.params ++ params)

  def withHeaders(headers: (String, String)*) = copy(headers = headers)
  def addHeaders(headers: (String, String)*) = copy(headers = this.headers ++ headers)

  def compactDescription: String = {
    val builder = new StringBuilder()
    builder.append("open ")
    builder.append(stream.name)
    builder.append(" to ")
    builder.append(url)
    if (params.nonEmpty) {
      builder.append(" with query parameters ")
      printArrowPairsBuilder(params, builder)
    }
    if (headers.nonEmpty) {
      builder.append(" with headers ")
      printArrowPairsBuilder(headers, builder)
    }
    builder.result()
  }

  def detailedDescription: String = {
    val builder = new StringBuilder()
    builder.append(stream.name)
    builder.append(" request to ")
    builder.append(url)
    builder.append("\n")

    // parameters
    if (params.isEmpty)
      builder.append("without parameters")
    else {
      builder.append("with parameters ")
      printArrowPairsBuilder(params, builder)
    }
    builder.append("\n")

    // headers
    if (headers.isEmpty)
      builder.append("without headers")
    else {
      builder.append("with headers ")
      printArrowPairsBuilder(headers, builder)
    }
    builder.append("\n")

    builder.result()
  }
}
