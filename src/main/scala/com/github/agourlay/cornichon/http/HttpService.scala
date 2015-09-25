package com.github.agourlay.cornichon.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.stream.ActorMaterializer
import cats.data.Xor
import com.github.agourlay.cornichon.core._
import de.heikoseeberger.akkasse.ServerSentEvent
import org.json4s._
import org.json4s.native.JsonMethods._
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext }

class HttpService {

  implicit private val system = ActorSystem("cornichon-http-feature")
  implicit private val mat = ActorMaterializer()
  implicit private val ec: ExecutionContext = system.dispatcher
  private val client = new HttpClient

  val LastResponseBodyKey = "last-response-body"
  lazy val LastResponseStatusKey = "last-response-status"
  val LastResponseHeadersKey = "last-response-headers"
  val WithHeadersKey = "with-headers"

  val HeadersKeyValueDelim = '|'

  private case class InternalSSE(data: String, eventType: Option[String] = None, id: Option[String] = None)

  private object InternalSSE {
    def build(sse: ServerSentEvent): InternalSSE = InternalSSE(sse.data, sse.eventType, sse.id)
    implicit val formatServerSentEvent = jsonFormat3(InternalSSE.apply)
  }

  def Post(payload: JValue, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      payloadResolved ← Resolver.fillPlaceholder(payload)(s.content)
      urlResolved ← Resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(client.postJson(payloadResolved, encodeParams(urlResolved, params), headers ++ extractWithHeadersSession(s)), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  def Put(payload: JValue, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      payloadResolved ← Resolver.fillPlaceholder(payload)(s.content)
      urlResolved ← Resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(client.putJson(payloadResolved, encodeParams(urlResolved, params), headers ++ extractWithHeadersSession(s)), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  def Get(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      urlResolved ← Resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(client.getJson(encodeParams(urlResolved, params), headers ++ extractWithHeadersSession(s)), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  def GetSSE(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[HttpHeader])(s: Session) =
    for {
      urlResolved ← Resolver.fillPlaceholder(url)(s.content)
    } yield {
      val res = Await.result(client.getSSE(encodeParams(urlResolved, params), takeWithin, headers ++ extractWithHeadersSession(s)), takeWithin + 1.second)
      val jsonRes = res.map(s ⇒ InternalSSE.build(s)).toVector.toJson
      // TODO add Headers and Status Code
      (jsonRes, s.addValue(LastResponseBodyKey, jsonRes.prettyPrint))
    }

  def Delete(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      urlResolved ← Resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(client.deleteJson(encodeParams(urlResolved, params), headers ++ extractWithHeadersSession(s)), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  // TODO rewrite fragile code
  private def encodeParams(url: String, params: Seq[(String, String)]) = {
    def formatEntry(entry: (String, String)) = s"${entry._1}=${entry._2}"
    val encoded =
      if (params.isEmpty) ""
      else if (params.size == 1) s"?${formatEntry(params.head)}"
      else s"?${formatEntry(params.head)}" + params.tail.map { e ⇒ s"&${formatEntry(e)}" }.mkString
    url + encoded
  }

  def fillInHttpSession(session: Session, response: CornichonHttpResponse): Session =
    session
      .addValue(LastResponseStatusKey, response.status.intValue().toString)
      .addValue(LastResponseBodyKey, response.body)
      .addValue(LastResponseHeadersKey, response.headers.map(h ⇒ s"${h.name()}$HeadersKeyValueDelim${h.value()}").mkString(","))

  def parseHttpHeaders(headers: Seq[(String, String)]): Seq[HttpHeader] =
    headers.map(v ⇒ HttpHeader.parse(v._1, v._2)).map {
      case ParsingResult.Ok(h, e) ⇒ h
      case ParsingResult.Error(e) ⇒ throw new MalformedHeadersError(e.formatPretty)
    }

  def extractWithHeadersSession(session: Session): Seq[HttpHeader] =
    session.getKey(WithHeadersKey).fold(Seq.empty[HttpHeader]) { headers ⇒
      val tuples = headers.split(',').toSeq.map { header ⇒
        val elms = header.split(HeadersKeyValueDelim)
        (elms.head, elms.tail.head)
      }
      parseHttpHeaders(tuples)
    }

  implicit private def toSprayJson(jValue: JValue): JsValue = compact(render(jValue)).parseJson
}
