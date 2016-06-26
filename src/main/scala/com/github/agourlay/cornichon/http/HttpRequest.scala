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
  val url: String
  val params: Seq[(String, String)]
  val headers: Seq[(String, String)]

  def description: String

  def paramsTitle = if (params.isEmpty) "" else s" with params ${displayTuples(params)}"
  def headersTitle = if (headers.isEmpty) "" else s" with headers ${displayTuples(headers)}"
}

case class HttpRequest(method: HttpMethod, url: String, payload: Option[String], params: Seq[(String, String)], headers: Seq[(String, String)])
    extends BaseRequest {

  def withParams(params: (String, String)*) = copy(params = params)
  def addParams(params: (String, String)*) = copy(params = this.params ++ params)

  def withHeaders(headers: (String, String)*) = copy(headers = headers)
  def addHeaders(headers: (String, String)*) = copy(headers = this.headers ++ headers)

  def withPayload(payload: String) = copy(payload = Some(payload))

  def description: String = {
    val base = s"${method.name} $url"
    val payloadTitle = payload.fold("")(p ⇒ s" with payload $p")
    base + payloadTitle + paramsTitle + headersTitle
  }
}

case class HttpStream(name: String)

object HttpStreams {
  val SSE = HttpStream("SSE")
  val WS = HttpStream("WS")
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
  val name = "POST GraphQL query"

  def withQuery(query: Document) = copy(query = query)
  def withOperationName(operationName: String) = copy(operationName = Some(operationName))
  def withVariables(newVariables: (String, Any)*) = {
    val toJsonTuples = newVariables.map { case (k, v) ⇒ k → parseJsonUnsafe(v) }
    copy(variables = variables.fold(Some(toJsonTuples.toMap))(v ⇒ Some(v ++ toJsonTuples)))
  }
}

