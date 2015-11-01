package com.github.agourlay.cornichon.http

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import cats.data.Xor
import cats.data.Xor.{ left, right }
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.http.client.HttpClient
import org.json4s._

import scala.concurrent.duration._
import scala.concurrent.{ Future, Await, ExecutionContext }

class HttpService(baseUrl: String, requestTimeout: FiniteDuration, client: HttpClient, resolver: Resolver, parser: CornichonJson)(implicit ec: ExecutionContext) {

  val LastResponseBodyKey = "last-response-body"
  val LastResponseStatusKey = "last-response-status"
  val LastResponseHeadersKey = "last-response-headers"
  val WithHeadersKey = "with-headers"

  val HeadersKeyValueDelim = '|'

  private type WithPayloadCall = (JValue, String, Seq[(String, String)], Seq[HttpHeader]) ⇒ Future[Xor[HttpError, CornichonHttpResponse]]
  private type WithoutPayloadCall = (String, Seq[(String, String)], Seq[HttpHeader]) ⇒ Future[Xor[HttpError, CornichonHttpResponse]]
  private type StreamedPayloadCall = (String, Seq[(String, String)], FiniteDuration, Seq[HttpHeader]) ⇒ Future[Xor[HttpError, CornichonHttpResponse]]

  private def withPayload(call: WithPayloadCall, payload: String, url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      payloadResolved ← resolver.fillPlaceholders(payload)(s)
      paramsResolved ← resolver.tuplesResolver(params, s)
      headersResolved ← resolver.tuplesResolver(headers, s)
      parsedHeaders ← parseHttpHeaders(headersResolved)
      extractedHeaders ← extractWithHeadersSession(s)
      json ← parser.parseJsonXor(payloadResolved)
      urlResolved ← resolver.fillPlaceholders(urlBuilder(url))(s)
      res ← Await.result(call(json, urlResolved, paramsResolved, parsedHeaders ++ extractedHeaders), requestTimeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  private def withoutPayload(call: WithoutPayloadCall, url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      urlResolved ← resolver.fillPlaceholders(urlBuilder(url))(s)
      paramsResolved ← resolver.tuplesResolver(params, s)
      headersResolved ← resolver.tuplesResolver(headers, s)
      parsedHeaders ← parseHttpHeaders(headersResolved)
      extractedHeaders ← extractWithHeadersSession(s)
      res ← Await.result(call(urlResolved, paramsResolved, parsedHeaders ++ extractedHeaders), requestTimeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  private def streamedPayload(call: StreamedPayloadCall, url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      urlResolved ← resolver.fillPlaceholders(urlBuilder(url))(s)
      paramsResolved ← resolver.tuplesResolver(params, s)
      headersResolved ← resolver.tuplesResolver(headers, s)
      parsedHeaders ← parseHttpHeaders(headersResolved)
      extractedHeaders ← extractWithHeadersSession(s)
      res ← Await.result(call(urlResolved, paramsResolved, takeWithin, parsedHeaders ++ extractedHeaders), takeWithin + 1.second)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  def Post(url: String, payload: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session): Session =
    withPayload(client.postJson, payload, url, params, headers)(s).map { case (_, session) ⇒ session }.fold(e ⇒ throw e, identity)

  def Put(url: String, payload: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session): Session =
    withPayload(client.putJson, payload, url, params, headers)(s).map { case (_, session) ⇒ session }.fold(e ⇒ throw e, identity)

  def Get(url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session): Session =
    withoutPayload(client.getJson, url, params, headers)(s).map { case (_, session) ⇒ session }.fold(e ⇒ throw e, identity)

  def Delete(url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session): Session =
    withoutPayload(client.deleteJson, url, params, headers)(s).map { case (_, session) ⇒ session }.fold(e ⇒ throw e, identity)

  def GetSSE(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session) =
    streamedPayload(client.getSSE, url, takeWithin, params, headers)(s).map { case (_, session) ⇒ session }.fold(e ⇒ throw e, identity)

  def GetWS(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session) =
    streamedPayload(client.getWS, url, takeWithin, params, headers)(s).map { case (_, session) ⇒ session }.fold(e ⇒ throw e, identity)

  def fillInHttpSession(session: Session, response: CornichonHttpResponse): Session =
    session
      .addValue(LastResponseStatusKey, response.status.intValue().toString)
      .addValue(LastResponseBodyKey, response.body)
      .addValue(LastResponseHeadersKey, response.headers.map(h ⇒ s"${h.name()}$HeadersKeyValueDelim${h.value()}").mkString(","))

  // TODO accumulate errors
  def parseHttpHeaders(headers: Seq[(String, String)]): Xor[MalformedHeadersError, Seq[HttpHeader]] = {
    def loop(headers: Seq[(String, String)], acc: Seq[HttpHeader]): Xor[MalformedHeadersError, Seq[HttpHeader]] = {
      if (headers.isEmpty) right(acc)
      else {
        val (name, value) = headers.head
        HttpHeader.parse(name, value) match {
          case ParsingResult.Ok(h, e) ⇒ loop(headers.tail, acc :+ h)
          case ParsingResult.Error(e) ⇒ left(MalformedHeadersError(e.formatPretty))
        }
      }
    }
    loop(headers, Seq.empty[HttpHeader])
  }

  def extractWithHeadersSession(session: Session): Xor[MalformedHeadersError, Seq[HttpHeader]] =
    session.getOpt(WithHeadersKey).fold[Xor[MalformedHeadersError, Seq[HttpHeader]]](right(Seq.empty[HttpHeader])) { headers ⇒
      val tuples = headers.split(',').toSeq.map { header ⇒
        val elms = header.split(HeadersKeyValueDelim)
        (elms.head, elms.tail.head)
      }
      parseHttpHeaders(tuples)
    }

  private def urlBuilder(input: String) =
    if (baseUrl.isEmpty) input
    else baseUrl + input
}
