package com.github.agourlay.cornichon.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.stream.ActorMaterializer
import cats.data.Xor
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.http.CornichonJson._
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

  //TODO resolve placeholders in param and then transform it
  //TODO factorize with/without payload?

  def Post(payload: String, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      payloadResolved ← Resolver.fillPlaceholder(payload)(s.content)
      json ← parseJson(payloadResolved)
      urlResolved ← Resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(client.postJson(json, urlResolved, params, headers ++ extractWithHeadersSession(s)), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  def Put(payload: String, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      payloadResolved ← Resolver.fillPlaceholder(payload)(s.content)
      json ← parseJson(payloadResolved)
      urlResolved ← Resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(client.putJson(json, urlResolved, params, headers ++ extractWithHeadersSession(s)), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  def Get(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      urlResolved ← Resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(client.getJson(urlResolved, params, headers ++ extractWithHeadersSession(s)), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  def GetSSE(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[HttpHeader])(s: Session) =
    for {
      urlResolved ← Resolver.fillPlaceholder(url)(s.content)
    } yield {
      val res = Await.result(client.getSSE(urlResolved, params, takeWithin, headers ++ extractWithHeadersSession(s)), takeWithin + 1.second)
      val jsonRes = res.map(s ⇒ InternalSSE.build(s)).toVector.toJson
      // TODO add Headers and Status Code
      (jsonRes, s.addValue(LastResponseBodyKey, jsonRes.prettyPrint))
    }

  def Delete(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader])(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (CornichonHttpResponse, Session)] =
    for {
      urlResolved ← Resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(client.deleteJson(urlResolved, params, headers ++ extractWithHeadersSession(s)), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }

  def fillInHttpSession(session: Session, response: CornichonHttpResponse): Session =
    session
      .addValue(LastResponseStatusKey, response.status.intValue().toString)
      .addValue(LastResponseBodyKey, response.body)
      .addValue(LastResponseHeadersKey, response.headers.map(h ⇒ s"${h.name()}$HeadersKeyValueDelim${h.value()}").mkString(","))

  // TODO return XOR
  def parseHttpHeaders(headers: Seq[(String, String)]): Seq[HttpHeader] =
    headers.map(v ⇒ HttpHeader.parse(v._1, v._2)).map {
      case ParsingResult.Ok(h, e) ⇒ h
      case ParsingResult.Error(e) ⇒ throw new MalformedHeadersError(e.formatPretty)
    }

  //private def paramsResolver(params: Seq[(String, String)], session: Session): Xor[ResolverError, Seq[(String, String)]] = {
  //  params.map{
  //    case (name, value) => (Resolver.fillPlaceholder(name)(session.content), Resolver.fillPlaceholder(value)(session.content))
  //  }
  //}

  def extractWithHeadersSession(session: Session): Seq[HttpHeader] =
    session.getOpt(WithHeadersKey).fold(Seq.empty[HttpHeader]) { headers ⇒
      val tuples = headers.split(',').toSeq.map { header ⇒
        val elms = header.split(HeadersKeyValueDelim)
        (elms.head, elms.tail.head)
      }
      parseHttpHeaders(tuples)
    }

  implicit private def toSprayJson(jValue: JValue): JsValue = compact(render(jValue)).parseJson
}
