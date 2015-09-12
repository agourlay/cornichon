package com.github.agourlay.cornichon.dsl

import akka.http.scaladsl.model.HttpHeader
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.http._
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.concurrent.duration._
import scala.util.{ Success, Try }

trait HttpDsl extends Dsl {
  this: HttpFeature ⇒

  implicit val requestTimeout: FiniteDuration = 2000 millis

  sealed trait Request { val name: String }

  sealed trait WithoutPayload extends Request {
    def apply(url: String, params: (String, String)*)(implicit headers: Seq[HttpHeader] = Seq.empty) =
      ExecutableStep(
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
      ExecutableStep(
        title = {
        val base = s"$name to $url with payload $payload"
        if (params.isEmpty) base
        else s"$base with params ${params.mkString(", ")}"
      },
        action = s ⇒ {
        val x = this match {
          case POST ⇒ Post(parse(payload), url, params, headers)(s)
          case PUT  ⇒ Put(parse(payload), url, params, headers)(s)
        }
        x.map { case (jsonRes, session) ⇒ (true, session) }.fold(e ⇒ throw e, identity)
      },
        expected = true
      )
  }

  sealed trait Streamed extends Request {
    def apply(url: String, takeWithin: FiniteDuration, params: (String, String)*)(implicit headers: Seq[HttpHeader] = Seq.empty) =
      ExecutableStep(
        title = {
        val base = s"$name $url"
        if (params.isEmpty) base
        else s"$base with params ${params.mkString(", ")}"
      },
        action = s ⇒ {
        val x = this match {
          case GET_SSE ⇒ GetSSE(url, takeWithin, params, headers)(s)
          case GET_WS  ⇒ ???
        }
        x.map { case (source, session) ⇒ (true, session) }.fold(e ⇒ throw e, identity)
      },
        expected = true
      )
  }

  case object GET extends WithoutPayload { val name = "GET" }

  case object DELETE extends WithoutPayload { val name = "DELETE" }

  case object POST extends WithPayload { val name = "POST" }

  case object PUT extends WithPayload { val name = "PUT" }

  case object GET_SSE extends Streamed { val name = "GET SSE" }

  case object GET_WS extends Streamed { val name = "GET WS" }

  def status_is(status: Int) = session_contains(LastResponseStatusKey, status.toString, Some(s"HTTP status is $status"))

  def headers_contain(headers: (String, String)*) =
    transform_assert_session(LastResponseHeadersKey, true, sessionHeaders ⇒ {
      val sessionHeadersValue = sessionHeaders.split(",")
      headers.forall { case (name, value) ⇒ sessionHeadersValue.contains(s"$name:$value") }
    }, Some(s"HTTP headers contain ${headers.mkString(", ")}"))

  def response_is(jsString: String, whiteList: Boolean = false): ExecutableStep[JValue] = {
    val jsonInput = parse(jsString)
    transform_assert_session(LastResponseJsonKey, jsonInput, sessionValue ⇒ {
      val sessionValueJson = parse(sessionValue)
      if (whiteList) {
        val Diff(changed, _, deleted) = jsonInput.diff(sessionValueJson)
        if (deleted != JNothing) throw new WhileListError(s"White list error - '$deleted' is not defined in object '$sessionValueJson")
        if (changed != JNothing) changed else jsonInput
      } else sessionValueJson
    }, Some(s"HTTP response is $jsString with whiteList=$whiteList"))
  }

  def response_is(jsString: String, ignoredKeys: String*): ExecutableStep[JValue] =
    transform_assert_session(LastResponseJsonKey, parse(jsString), sessionValue ⇒ {
      val jsonSessionValue = parse(sessionValue)
      if (ignoredKeys.isEmpty) jsonSessionValue
      else filterJsonKeys(jsonSessionValue, ignoredKeys)
    },
      title = if (ignoredKeys.isEmpty) Some(s"HTTP response is $jsString")
    else Some(s"HTTP response is $jsString ignoring keys ${ignoredKeys.map(v ⇒ s"'$v'").mkString(", ")}"))

  def filterJsonKeys(input: JValue, keys: Seq[String]): JValue =
    keys.foldLeft(input)((j, k) ⇒ j.removeField(_._1 == k))

  def extract_from_response(extractor: JValue ⇒ JValue, target: String) =
    extract_from_session(LastResponseJsonKey, s ⇒ extractor(parse(s)).values.toString, target)

  def extract_from_response(rootKey: String, target: String) =
    extract_from_session(LastResponseJsonKey, s ⇒ (parse(s) \ rootKey).values.toString, target)

  def response_is(mapFct: JValue ⇒ JValue, jsString: String) = {
    transform_assert_session(
      LastResponseJsonKey,
      expected = JString(jsString),
      sessionValue ⇒ mapFct(parse(sessionValue)), Some(s"HTTP response with transformation is $jsString")
    )
  }

  def show_last_status = show_session(LastResponseStatusKey)

  def show_last_response_json = show_session(LastResponseJsonKey)

  def show_last_response_headers = show_session(LastResponseHeadersKey)

  def response_array_is(expected: String, ordered: Boolean = true): ExecutableStep[Iterable[JValue]] =
    stringToJson(expected) match {
      case expectedArray: JArray ⇒
        if (ordered) response_array_transform(_.arr, expectedArray.arr, Some(s"response array is $expected"))
        else response_array_transform(s ⇒ s.arr.toSet, expectedArray.arr.toSet, Some(s"response array not ordered is $expected"))
      case _ ⇒
        throw new NotAnArrayError(expected)
    }

  def response_array_transform[A](mapFct: JArray ⇒ A, expected: A, title: Option[String]): ExecutableStep[A] =
    transform_assert_session[A](LastResponseJsonKey, expected, sessionValue ⇒ {
      parse(sessionValue) match {
        case arr: JArray ⇒
          log.debug(s"response_body_array_is applied to ${pretty(render(arr))}")
          mapFct(arr)
        case _ ⇒ throw new NotAnArrayError(sessionValue)
      }
    }, title)

  def response_array_size_is(size: Int) = response_array_transform(_.arr.size, size, Some(s"response array size is $size"))

  def response_array_contains(element: String) = response_array_transform(_.arr.contains(parse(element)), true, Some(s"response array contains $element"))

  def response_array_does_not_contain(element: String) = response_array_transform(_.arr.contains(parse(element)), false, Some(s"response array does not contain $element"))

  def response_against_schema(schemaUrl: String) = {
    val jsonSchema = loadJsonSchemaFile(schemaUrl)
    transform_assert_session(LastResponseJsonKey, Success, sessionValue ⇒ {
      val jsonNode = mapper.readTree(sessionValue)
      Try { jsonSchema.validate(jsonNode) }
    },
      title = Some(s"HTTP response is valid against JSON schema $schemaUrl"))
  }

  private def stringToJson(input: String): JValue =
    if (input.trim.head != '|') parse(input)
    else parse(DataTableParser.parseDataTable(input).asJson.toString())
}