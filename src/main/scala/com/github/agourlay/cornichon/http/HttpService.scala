package com.github.agourlay.cornichon.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.stream.ActorMaterializer
import cats.data.Xor
import cats.data.Xor.{ left, right }
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Resolver._
import com.github.agourlay.cornichon.http.CornichonJson._
import org.json4s._

import scala.concurrent.duration._
import scala.concurrent.{ Future, Await, ExecutionContext }

class HttpService(baseUrl: String, requestTimeout: FiniteDuration) {

  implicit private val system = ActorSystem("cornichon-http-feature")
  implicit private val mat = ActorMaterializer()
  implicit private val ec: ExecutionContext = system.dispatcher

  private val client = new HttpClient

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
      payloadResolved ← fillPlaceholders(payload)(s.content)
      paramsResolved ← tuplesResolver(params, s)
      headersResolved ← tuplesResolver(headers, s)
      parsedHeaders ← parseHttpHeaders(headersResolved)
      extractedHeaders ← extractWithHeadersSession(s)
      json ← parseJsonXor(payloadResolved)
      urlResolved ← fillPlaceholders(urlBuilder(url))(s.content)
      res ← Await.result(call(json, urlResolved, paramsResolved, parsedHeaders ++ extractedHeaders), requestTimeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  private def withoutPayload(call: WithoutPayloadCall, url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      urlResolved ← fillPlaceholders(urlBuilder(url))(s.content)
      paramsResolved ← tuplesResolver(params, s)
      headersResolved ← tuplesResolver(headers, s)
      parsedHeaders ← parseHttpHeaders(headersResolved)
      extractedHeaders ← extractWithHeadersSession(s)
      res ← Await.result(call(urlResolved, paramsResolved, parsedHeaders ++ extractedHeaders), requestTimeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  private def streamedPayload(call: StreamedPayloadCall, url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      urlResolved ← fillPlaceholders(urlBuilder(url))(s.content)
      paramsResolved ← tuplesResolver(params, s)
      headersResolved ← tuplesResolver(headers, s)
      parsedHeaders ← parseHttpHeaders(headersResolved)
      extractedHeaders ← extractWithHeadersSession(s)
      res ← Await.result(call(urlResolved, paramsResolved, takeWithin, parsedHeaders ++ extractedHeaders), takeWithin + 1.second)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  def Post(payload: String, url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    withPayload(client.postJson, payload, url, params, headers)(s)

  def Put(payload: String, url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    withPayload(client.putJson, payload, url, params, headers)(s)

  def Get(url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    withoutPayload(client.getJson, url, params, headers)(s)

  def Delete(url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    withoutPayload(client.deleteJson, url, params, headers)(s)

  def GetSSE(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session) =
    streamedPayload(client.getSSE, url, takeWithin, params, headers)(s)

  def GetWS(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session) =
    streamedPayload(client.getWS, url, takeWithin, params, headers)(s)

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
