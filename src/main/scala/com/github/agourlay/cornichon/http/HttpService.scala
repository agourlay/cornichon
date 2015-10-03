package com.github.agourlay.cornichon.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.stream.ActorMaterializer
import cats.data.Xor
import cats.data.Xor.{ left, right }
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.http.CornichonJson._
import de.heikoseeberger.akkasse.ServerSentEvent
import org.json4s._
import org.json4s.native.JsonMethods._
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ Future, Await, ExecutionContext }

class HttpService(baseUrl: String = "") {

  implicit private val system = ActorSystem("cornichon-http-feature")
  implicit private val mat = ActorMaterializer()
  implicit private val ec: ExecutionContext = system.dispatcher
  private val client = new HttpClient

  val LastResponseBodyKey = "last-response-body"
  val LastResponseStatusKey = "last-response-status"
  val LastResponseHeadersKey = "last-response-headers"
  val WithHeadersKey = "with-headers"

  val HeadersKeyValueDelim = '|'

  private type WithPayloadCall = (JsValue, String, Seq[(String, String)], Seq[HttpHeader]) ⇒ Future[Xor[HttpError, CornichonHttpResponse]]
  private type WithoutPayloadCall = (String, Seq[(String, String)], Seq[HttpHeader]) ⇒ Future[Xor[HttpError, CornichonHttpResponse]]

  private def withPayload(call: WithPayloadCall, payload: String, url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      payloadResolved ← Resolver.fillPlaceholders(payload)(s.content)
      paramsResolved ← Resolver.tuplesResolver(params, s)
      headersResolved ← Resolver.tuplesResolver(headers, s)
      parsedHeaders ← parseHttpHeaders(headersResolved)
      extractedHeaders ← extractWithHeadersSession(s)
      json ← parseJsonXor(payloadResolved)
      urlResolved ← Resolver.fillPlaceholders(urlBuilder(url))(s.content)
      res ← Await.result(call(json, urlResolved, paramsResolved, parsedHeaders ++ extractedHeaders), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  private def withoutPayload(call: WithoutPayloadCall, url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      urlResolved ← Resolver.fillPlaceholders(urlBuilder(url))(s.content)
      paramsResolved ← Resolver.tuplesResolver(params, s)
      headersResolved ← Resolver.tuplesResolver(headers, s)
      parsedHeaders ← parseHttpHeaders(headersResolved)
      extractedHeaders ← extractWithHeadersSession(s)
      res ← Await.result(call(urlResolved, paramsResolved, parsedHeaders ++ extractedHeaders), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  def Post(payload: String, url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    withPayload(client.postJson, payload: String, url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session)(timeout: FiniteDuration)

  def Put(payload: String, url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    withPayload(client.putJson, payload: String, url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session)(timeout: FiniteDuration)

  def Get(url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    withoutPayload(client.getJson, url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session)(timeout: FiniteDuration)

  def Delete(url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    withoutPayload(client.deleteJson, url: String, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session)(timeout: FiniteDuration)

  private case class InternalSSE(data: String, eventType: Option[String] = None, id: Option[String] = None)

  private object InternalSSE {
    def build(sse: ServerSentEvent): InternalSSE = InternalSSE(sse.data, sse.eventType, sse.id)
    implicit val formatServerSentEvent = jsonFormat3(InternalSSE.apply)
  }

  def GetSSE(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session) =
    for {
      urlResolved ← Resolver.fillPlaceholders(urlBuilder(url))(s.content)
      paramsResolved ← Resolver.tuplesResolver(params, s)
      parsedHeaders ← parseHttpHeaders(headers)
      extractedHeaders ← extractWithHeadersSession(s)
    } yield {
      val res = Await.result(client.getSSE(urlResolved, paramsResolved, takeWithin, parsedHeaders ++ extractedHeaders), takeWithin + 1.second)
      val jsonRes = res.map(s ⇒ InternalSSE.build(s)).toVector.toJson
      // TODO add Headers and Status Code
      (jsonRes, s.addValue(LastResponseBodyKey, jsonRes.prettyPrint))
    }

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

  implicit private def toSprayJson(jValue: JValue): JsValue = compact(render(jValue)).parseJson
}
