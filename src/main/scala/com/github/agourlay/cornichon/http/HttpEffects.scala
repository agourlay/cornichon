package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.dsl.Dsl._
import com.github.agourlay.cornichon.json.CornichonJson._
import sangria.ast.Document
import sangria.renderer.QueryRenderer

import scala.concurrent.duration.FiniteDuration

object HttpEffects {

  sealed trait HttpRequest {
    def name: String
    def url: String

    def params: Seq[(String, String)]
    def withParams(params: (String, String)*): HttpRequest

    def headers: Seq[(String, String)]
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

  case class Head(url: String, params: Seq[(String, String)], headers: Seq[(String, String)]) extends HttpRequest {
    val name = "HEAD"

    def withParams(params: (String, String)*) = copy(params = params)
    def withHeaders(headers: (String, String)*) = copy(headers = headers)
  }

  case class Options(url: String, params: Seq[(String, String)], headers: Seq[(String, String)]) extends HttpRequest {
    val name = "OPTIONS"

    def withParams(params: (String, String)*) = copy(params = params)
    def withHeaders(headers: (String, String)*) = copy(headers = headers)
  }

  case class Delete(url: String, params: Seq[(String, String)], headers: Seq[(String, String)]) extends HttpRequest {
    val name = "DELETE"

    def withParams(params: (String, String)*) = copy(params = params)
    def withHeaders(headers: (String, String)*) = copy(headers = headers)

  }

  sealed trait HttpRequestWithPayload extends HttpRequest {
    def payload: String

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

  case class Patch(url: String, payload: String, params: Seq[(String, String)], headers: Seq[(String, String)]) extends HttpRequestWithPayload {
    val name = "PATCH"
    def withParams(params: (String, String)*) = copy(params = params)
    def withHeaders(headers: (String, String)*) = copy(headers = headers)
  }

  case class QueryGQL(url: String, payload: String, params: Seq[(String, String)], headers: Seq[(String, String)],
      query: Document, operationName: Option[String] = None, variables: Option[Map[String, String]] = None) extends HttpRequestWithPayload {
    val name = "Query GQL"

    def withParams(params: (String, String)*) = copy(params = params)
    def withHeaders(headers: (String, String)*) = copy(headers = headers)

    //GQL builder
    def withQuery(query: Document) = copy(query = query).buildBody()
    def withOperationName(operationName: String) = copy(operationName = Some(operationName)).buildBody()
    def withVariables(newVariables: (String, String)*) = copy(variables = variables.fold(Some(newVariables.toMap))(v ⇒ Some(v ++ newVariables))).buildBody()

    def buildBody() = {
      import io.circe.generic.auto._
      import io.circe.syntax._

      val queryDoc = query.source.getOrElse(QueryRenderer.render(query, QueryRenderer.Pretty))
      val newPayload = GqlPayload(queryDoc, operationName, variables)
      copy(payload = prettyPrint(newPayload.asJson))
    }
  }

  private case class GqlPayload(query: String, operationName: Option[String], variables: Option[Map[String, String]])

  sealed trait HttpRequestStreamed extends HttpRequest {
    def takeWithin: FiniteDuration
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
