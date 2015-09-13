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
        action =
        s ⇒ {
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
        action =
        s ⇒ {
          val x = this match {
            case POST ⇒ Post(dslParse(payload), url, params, headers)(s)
            case PUT  ⇒ Put(dslParse(payload), url, params, headers)(s)
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
        action =
        s ⇒ {
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

  def response_is(mapFct: JValue ⇒ JValue, input: String) =
    transform_assert_session(
      key = LastResponseJsonKey,
      expected = dslParse(input),
      sessionValue ⇒ mapFct(dslParse(sessionValue)),
      title = Some(s"HTTP response with transformation is $input")
    )

  def response_is(input: String, whiteList: Boolean = false): ExecutableStep[JValue] = {
    val jsonInput = dslParse(input)
    transform_assert_session(
      key = LastResponseJsonKey,
      title = Some(s"HTTP response is $input with whiteList=$whiteList"),
      expected = jsonInput,
      mapValue =
        sessionValue ⇒ {
          val sessionValueJson = dslParse(sessionValue)
          if (whiteList) {
            val Diff(changed, _, deleted) = jsonInput.diff(sessionValueJson)
            if (deleted != JNothing) throw new WhileListError(s"White list error - '$deleted' is not defined in object '$sessionValueJson")
            if (changed != JNothing) changed else jsonInput
          } else sessionValueJson
        }
    )
  }

  def response_is(jsString: String, ignoring: String*): ExecutableStep[JValue] =
    transform_assert_session(
      key = LastResponseJsonKey,
      title = titleBuilder(s"HTTP response is '$jsString'", ignoring),
      expected = dslParse(jsString),
      mapValue =
        sessionValue ⇒ {
          val jsonSessionValue = dslParse(sessionValue)
          if (ignoring.isEmpty) jsonSessionValue
          else filterJsonKeys(jsonSessionValue, ignoring)
        }
    )

  def response_is(expected: String, ordered: Boolean, ignoring: String*): ExecutableStep[Iterable[JValue]] =
    dslParse(expected) match {
      case expectedArray: JArray ⇒
        if (ordered)
          response_array_transform(_.arr.map(filterJsonKeys(_, ignoring)), expectedArray.arr, titleBuilder(s"response array is $expected", ignoring))
        else
          response_array_transform(s ⇒ s.arr.map(filterJsonKeys(_, ignoring)).toSet, expectedArray.arr.toSet, titleBuilder(s"response array not ordered is $expected", ignoring))
      case _ ⇒
        failWith(new NotAnArrayError(dslParse(expected)), titleBuilder(s"response array is $expected", ignoring).get, Seq.empty[JValue])
    }

  def filterJsonKeys(input: JValue, keys: Seq[String]): JValue =
    keys.foldLeft(input)((j, k) ⇒ j.removeField(_._1 == k))

  def extract_from_response(extractor: JValue ⇒ JValue, target: String) =
    extract_from_session(LastResponseJsonKey, s ⇒ extractor(dslParse(s)).values.toString, target)

  def extract_from_response(rootKey: String, target: String) =
    extract_from_session(LastResponseJsonKey, s ⇒ (dslParse(s) \ rootKey).values.toString, target)

  def show_last_status = show_session(LastResponseStatusKey)

  def show_last_response_json = show_session(LastResponseJsonKey)

  def show_last_response_headers = show_session(LastResponseHeadersKey)

  private def titleBuilder(baseTitle: String, ignoring: Seq[String]): Option[String] =
    if (ignoring.isEmpty) Some(baseTitle)
    else Some(s"$baseTitle ignoring keys ${ignoring.map(v ⇒ s"'$v'").mkString(", ")}")

  def response_array_transform[A](mapFct: JArray ⇒ A, expected: A, title: Option[String]): ExecutableStep[A] =
    transform_assert_session[A](
      title = title,
      key = LastResponseJsonKey,
      expected = expected,
      mapValue =
      sessionValue ⇒ {
        dslParse(sessionValue) match {
          case arr: JArray ⇒
            logger.debug(s"response_body_array_is applied to ${pretty(render(arr))}")
            mapFct(arr)
          case _ ⇒ throw new NotAnArrayError(sessionValue)
        }
      }
    )

  def response_array_size_is(size: Int) = response_array_transform(_.arr.size, size, Some(s"response array size is $size"))

  def response_array_contains(element: String) = response_array_transform(_.arr.contains(parse(element)), true, Some(s"response array contains $element"))

  def response_array_does_not_contain(element: String) = response_array_transform(_.arr.contains(parse(element)), false, Some(s"response array does not contain $element"))

  def response_against_schema(schemaUrl: String) =
    transform_assert_session(
      key = LastResponseJsonKey,
      expected = Success,
      mapValue =
      sessionValue ⇒ {
        val jsonNode = mapper.readTree(sessionValue)
        Try { loadJsonSchemaFile(schemaUrl).validate(jsonNode) }
      },
      title = Some(s"HTTP response is valid against JSON schema $schemaUrl")
    )

  private def dslParse[A](input: A): JValue = input match {
    case s: String if s.trim.head == '|' ⇒ parse(DataTableParser.parseDataTable(s).asJson.toString())
    case s: String if s.trim.head == '{' ⇒ parse(s)
    case s: String if s.trim.head == '[' ⇒ parse(s)
    case s: String                       ⇒ JString(s)
    case d: Double                       ⇒ JDouble(d)
    case i: Int                          ⇒ JInt(i)
    case l: Long                         ⇒ JLong(l)
    case b: Boolean                      ⇒ JBool(b)
  }
}