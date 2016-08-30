package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.util.Formats._
import com.github.agourlay.cornichon.json.CornichonJson._
import io.circe.Json
import sangria.ast.Document

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

  def description: String

  def paramsTitle = if (params.isEmpty) "" else s" with query parameters ${displayTuples(params)}"
  def headersTitle = if (headers.isEmpty) "" else s" with headers ${displayTuples(headers)}"
}

case class HttpRequest[A: BodyInput](method: HttpMethod, url: String, body: Option[A], params: Seq[(String, String)], headers: Seq[(String, String)])
    extends BaseRequest {

  def withParams(params: (String, String)*) = copy(params = params)
  def addParams(params: (String, String)*) = copy(params = this.params ++ params)

  def withHeaders(headers: (String, String)*) = copy(headers = headers)
  def addHeaders(headers: (String, String)*) = copy(headers = this.headers ++ headers)

  def withBody[B: BodyInput](body: B) = copy(body = Some(body))

  def description: String = {
    val input = implicitly[BodyInput[A]]
    val base = s"${method.name} $url"
    val payloadTitle = body.fold("")(p ⇒ s" with body ${input.show(p)}")
    base + payloadTitle + paramsTitle + headersTitle
  }
}

trait HttpRequestsDsl extends BodyInputOps {
  import com.github.agourlay.cornichon.http.HttpMethods._
  import com.github.agourlay.cornichon.util.ShowInstances._

  def get(url: String) = HttpRequest[String](GET, url, None, Seq.empty, Seq.empty)
  def head(url: String) = HttpRequest[String](HEAD, url, None, Seq.empty, Seq.empty)
  def options(url: String) = HttpRequest[String](OPTIONS, url, None, Seq.empty, Seq.empty)
  def delete(url: String) = HttpRequest[String](DELETE, url, None, Seq.empty, Seq.empty)
  def post(url: String) = HttpRequest[String](POST, url, None, Seq.empty, Seq.empty)
  def put(url: String) = HttpRequest[String](PUT, url, None, Seq.empty, Seq.empty)
  def patch(url: String) = HttpRequest[String](PATCH, url, None, Seq.empty, Seq.empty)
}

object HttpRequest extends HttpRequestsDsl

case class HttpStream(name: String)

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

  def description: String = {
    val base = s"open ${stream.name} to $url"
    base + paramsTitle + headersTitle
  }
}

case class QueryGQL(url: String, query: Document, operationName: Option[String] = None, variables: Option[Map[String, Json]] = None) {

  def withQuery(query: Document) = copy(query = query)

  def withOperationName(operationName: String) = copy(operationName = Some(operationName))

  def withVariables(newVariables: (String, Any)*) = {
    val toJsonTuples = newVariables.map { case (k, v) ⇒ k → parseJsonUnsafe(v) }
    copy(variables = variables.fold(Some(toJsonTuples.toMap))(v ⇒ Some(v ++ toJsonTuples)))
  }
}

