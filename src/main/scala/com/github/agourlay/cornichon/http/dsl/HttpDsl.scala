package com.github.agourlay.cornichon.http.dsl

import akka.http.scaladsl.model.HttpHeader
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.dsl.{ DataTableParser, Dsl }
import com.github.agourlay.cornichon.http._
import spray.json._
import spray.json.DefaultJsonProtocol._
import scala.util._
import scala.concurrent.duration._

trait HttpDsl extends Dsl {
  this: HttpFeature ⇒

  implicit val requestTimeout: FiniteDuration = 2000 millis

  sealed trait Request { val name: String }

  sealed trait WithoutPayload extends Request {
    def apply(url: String, params: (String, String)*)(implicit headers: Seq[HttpHeader] = Seq.empty) =
      Step(
        title = {
        val base = s"$name $url"
        if (params.isEmpty) base
        else s"$base with params ${params.mkString(", ")}"
      },
        action = s ⇒ {
        val x = this match {
          case GET    ⇒ Get(url, params, headers)(s)
          case DELETE ⇒ Delete(url, params, headers)(s)
        }
        x.map { case (jsonRes, session) ⇒ (true, session) }.fold(e ⇒ throw e, identity)
      },
        expected = true
      )
  }

  sealed trait WithPayload extends Request {
    def apply(url: String, payload: String, params: (String, String)*)(implicit headers: Seq[HttpHeader] = Seq.empty) =
      Step(
        title = {
        val base = s"$name to $url with payload $payload"
        if (params.isEmpty) base
        else s"$base with params ${params.mkString(", ")}"
      },
        action = s ⇒ {
        val x = this match {
          case POST ⇒ Post(payload.parseJson, url, params, headers)(s)
          case PUT  ⇒ Put(payload.parseJson, url, params, headers)(s)
        }
        x.map { case (jsonRes, session) ⇒ (true, session) }.fold(e ⇒ throw e, identity)
      },
        expected = true
      )
  }

  case object GET extends WithoutPayload { val name = "GET" }

  case object DELETE extends WithoutPayload { val name = "DELETE" }

  case object POST extends WithPayload { val name = "POST" }

  case object PUT extends WithPayload { val name = "PUT" }

  def status_is(status: Int) = session_contains(LastResponseStatusKey, status.toString, Some(s"HTTP status is $status"))

  def headers_contain(headers: (String, String)*) =
    transform_assert_session(LastResponseHeadersKey, true, sessionHeaders ⇒ {
      val sessionHeadersValue = sessionHeaders.split(",")
      headers.forall { case (name, value) ⇒ sessionHeadersValue.contains(s"$name:$value") }
    }, Some(s"HTTP headers contain ${headers.mkString(", ")}"))

  def response_body_is(jsString: String, whiteList: Boolean = false): Step[JsValue] = {
    val jsonInput = jsString.parseJson
    transform_assert_session(LastResponseJsonKey, jsonInput, sessionValue ⇒ {
      val sessionValueJson = sessionValue.parseJson
      if (whiteList) {
        jsonInput.asJsObject.fields.map {
          case (k, v) ⇒
            val value = sessionValueJson.asJsObject.getFields(k)
            if (value.isEmpty) throw new WhileListError(s"White list error - key '$k' is not defined in object '$sessionValueJson")
            else (k, v)
        }.toJson
      } else sessionValueJson
    }, Some(s"HTTP response body is $jsString with whiteList=$whiteList"))
  }

  def response_body_is(jsString: String, ignoredKeys: String*): Step[JsValue] =
    transform_assert_session(LastResponseJsonKey, jsString.parseJson, sessionValue ⇒ {
      if (ignoredKeys.isEmpty) sessionValue.parseJson
      else sessionValue.parseJson.asJsObject.fields.filterKeys(!ignoredKeys.contains(_)).toJson
    }, Some(s"HTTP response body is $jsString"))

  def extract_from_response_body(extractor: JsValue ⇒ String, target: String) =
    extract_from_session(LastResponseJsonKey, s ⇒ extractor(s.parseJson), target)

  def response_body_is(mapFct: JsValue ⇒ String, jsString: String) =
    transform_assert_session(LastResponseJsonKey, jsString, sessionValue ⇒ {
      mapFct(sessionValue.parseJson)
    }, Some(s"HTTP response body with transformation is $jsString"))

  def show_last_status = show_session(LastResponseStatusKey)

  def show_last_response_json = show_session(LastResponseJsonKey)

  def show_last_response_headers = show_session(LastResponseHeadersKey)

  def response_body_array_is(expected: String, ordered: Boolean = true): Step[Iterable[JsValue]] =
    stringToJson(expected) match {
      case expectedArray: JsArray ⇒
        if (ordered) response_body_array_is(_.elements, expectedArray.elements, Some(s"response body array is $expected"))
        else response_body_array_is(s ⇒ s.elements.toSet, expectedArray.elements.toSet, Some(s"response body array not ordered is $expected"))
      case _ ⇒ throw new NotAnArrayError(expected)
    }

  def response_body_array_is[A](mapFct: JsArray ⇒ A, expected: A, title: Option[String]): Step[A] =
    transform_assert_session[A](LastResponseJsonKey, expected, sessionValue ⇒ {
      val sessionJSON = sessionValue.parseJson
      sessionJSON match {
        case arr: JsArray ⇒
          log.debug(s"response_body_array_is applied to ${arr.toString}")
          mapFct(arr)
        case _ ⇒ throw new NotAnArrayError(sessionJSON.toString())
      }
    }, title)

  def response_body_array_size_is(size: Int) = response_body_array_is(_.elements.size, size, Some(s"response body array size is $size"))

  def response_body_array_contains(element: String) = response_body_array_is(_.elements.contains(element.parseJson), true, Some(s"response body array contains $element"))

  def response_body_array_does_not_contain(element: String) = response_body_array_is(_.elements.contains(element.parseJson), false, Some(s"response body array does not contain $element"))

  private def stringToJson(input: String): JsValue =
    if (input.trim.head != '|') input.parseJson
    else DataTableParser.parseDataTable(input).asJson
}