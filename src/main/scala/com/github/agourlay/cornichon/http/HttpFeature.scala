package com.github.agourlay.cornichon.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.stream.ActorMaterializer
import cats.data.Xor
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.agourlay.cornichon.core._
import com.github.fge.jsonschema.main.{ JsonSchemaFactory, JsonSchema }
import de.heikoseeberger.akkasse.ServerSentEvent
import org.json4s._
import org.json4s.native.JsonMethods._

import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext }

trait HttpFeature {

  implicit private val system = ActorSystem("cornichon-http-feature")
  implicit private val mat = ActorMaterializer()
  implicit private val ec: ExecutionContext = system.dispatcher
  private val httpService = new HttpService

  lazy val LastResponseBodyKey = "last-response-body"
  lazy val LastResponseStatusKey = "last-response-status"
  lazy val LastResponseHeadersKey = "last-response-headers"
  lazy val WithHeadersKey = "with-headers"

  val HeadersKeyValueDelim = '|'
  val mapper = new ObjectMapper()

  case class InternalSSE(data: String, eventType: Option[String] = None, id: Option[String] = None)

  object InternalSSE {
    def build(sse: ServerSentEvent): InternalSSE = InternalSSE(sse.data, sse.eventType, sse.id)
    implicit val formatServerSentEvent = jsonFormat3(InternalSSE.apply)
  }

  def Post(payload: JValue, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      payloadResolved ← Resolver.fillPlaceholder(payload)(s.content)
      urlResolved ← Resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(httpService.postJson(payloadResolved, encodeParams(urlResolved, params), headers ++ sessionWithHeaders(s)), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  def Put(payload: JValue, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      payloadResolved ← Resolver.fillPlaceholder(payload)(s.content)
      urlResolved ← Resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(httpService.putJson(payloadResolved, encodeParams(urlResolved, params), headers ++ sessionWithHeaders(s)), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  def Get(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      urlResolved ← Resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(httpService.getJson(encodeParams(urlResolved, params), headers ++ sessionWithHeaders(s)), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  def GetSSE(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[HttpHeader])(s: Session) =
    for {
      urlResolved ← Resolver.fillPlaceholder(url)(s.content)
    } yield {
      val res = Await.result(httpService.getSSE(encodeParams(urlResolved, params), takeWithin, headers ++ sessionWithHeaders(s)), takeWithin + 1.second)
      val jsonRes = res.map(s ⇒ InternalSSE.build(s)).toVector.toJson
      // TODO add Headers and Status Code
      (jsonRes, s.addValue(LastResponseBodyKey, jsonRes.prettyPrint))
    }

  def Delete(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      urlResolved ← Resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(httpService.deleteJson(encodeParams(urlResolved, params), headers ++ sessionWithHeaders(s)), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  // rewrite later :)
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

  def loadJsonSchemaFile(fileLocation: String): JsonSchema =
    JsonSchemaFactory.newBuilder().freeze().getJsonSchema(fileLocation)

  def parseHttpHeaders(headers: Seq[(String, String)]): Seq[HttpHeader] =
    headers.map(v ⇒ HttpHeader.parse(v._1, v._2)).map {
      case ParsingResult.Ok(h, e) ⇒ h
      case ParsingResult.Error(e) ⇒ throw new MalformedHeadersError(e.formatPretty)
    }

  def sessionWithHeaders(session: Session): Seq[HttpHeader] =
    session.getKey(WithHeadersKey).fold(Seq.empty[HttpHeader]) { headers ⇒
      val tuples = headers.split(',').toSeq.map { header ⇒
        val elms = header.split(HeadersKeyValueDelim)
        (elms.head, elms.tail.head)
      }
      parseHttpHeaders(tuples)
    }

  implicit def toSprayJson(jValue: JValue): JsValue = compact(render(jValue)).parseJson
}
