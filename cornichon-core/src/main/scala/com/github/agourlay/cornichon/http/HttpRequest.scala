package com.github.agourlay.cornichon.http

import cats.Show
import cats.syntax.show._

import com.github.agourlay.cornichon.util.Printing._
import com.github.agourlay.cornichon.resolver.Resolvable

import io.circe.Encoder

import scala.concurrent.duration.FiniteDuration

case class HttpMethod(name: String)

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

  def compactDescription: String

  def paramsTitle: String = if (params.isEmpty) "" else s" with query parameters ${printArrowPairs(params)}"
  def headersTitle: String = if (headers.isEmpty) "" else s" with headers ${printArrowPairs(headers)}"
}

case class HttpRequest[A: Show: Resolvable: Encoder](method: HttpMethod, url: String, body: Option[A], params: Seq[(String, String)], headers: Seq[(String, String)])
  extends BaseRequest {

  def withParams(params: (String, String)*) = copy(params = params)
  def addParams(params: (String, String)*) = copy(params = this.params ++ params)

  def withHeaders(headers: (String, String)*) = copy(headers = headers)
  def addHeaders(headers: (String, String)*) = copy(headers = this.headers ++ headers)

  def withBody[B: Show: Resolvable: Encoder](body: B) = copy(body = Some(body))

  lazy val compactDescription: String = {
    val base = s"${method.name} $url"
    val payloadTitle = body.fold("")(p ⇒ s" with body ${p.show}")
    base + payloadTitle + paramsTitle + headersTitle
  }
}

trait HttpRequestsDsl {
  import com.github.agourlay.cornichon.http.HttpMethods._
  import cats.instances.string._

  def get(url: String): HttpRequest[String] = HttpRequest[String](GET, url, None, Nil, Nil)
  def head(url: String): HttpRequest[String] = HttpRequest[String](HEAD, url, None, Nil, Nil)
  def options(url: String): HttpRequest[String] = HttpRequest[String](OPTIONS, url, None, Nil, Nil)
  def delete(url: String): HttpRequest[String] = HttpRequest[String](DELETE, url, None, Nil, Nil)
  def post(url: String): HttpRequest[String] = HttpRequest[String](POST, url, None, Nil, Nil)
  def put(url: String): HttpRequest[String] = HttpRequest[String](PUT, url, None, Nil, Nil)
  def patch(url: String): HttpRequest[String] = HttpRequest[String](PATCH, url, None, Nil, Nil)
}

object HttpRequest extends HttpRequestsDsl {

  implicit def showRequest[A: Show] = new Show[HttpRequest[A]] {
    def show(r: HttpRequest[A]): String = {
      val body = r.body.fold("without body")(b ⇒ s"with body\n${b.show}")
      val params = if (r.params.isEmpty) "without parameters" else s"with parameters ${printArrowPairs(r.params)}"
      val headers = if (r.headers.isEmpty) "without headers" else s"with headers ${printArrowPairs(r.headers)}"

      s"""|HTTP ${r.method.name} request to ${r.url}
          |$params
          |$headers
          |$body""".stripMargin
    }
  }

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

  lazy val compactDescription: String = {
    val base = s"open ${stream.name} to $url"
    base + paramsTitle + headersTitle
  }
}

object HttpStreamedRequest {

  implicit val showStreamedRequest = new Show[HttpStreamedRequest] {
    def show(r: HttpStreamedRequest): String = {
      val params = if (r.params.isEmpty) "without parameters" else s"with parameters ${printArrowPairs(r.params)}"
      val headers = if (r.headers.isEmpty) "without headers" else s"with headers ${printArrowPairs(r.headers)}"

      s"""|${r.stream.name} request to ${r.url}
          |$params
          |$headers""".stripMargin
    }
  }

}