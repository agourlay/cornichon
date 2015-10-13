package com.github.agourlay.cornichon.dsl

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.ExecutableStep._
import com.github.agourlay.cornichon.http.CornichonJson._
import com.github.agourlay.cornichon.http.StatusError
import com.github.fge.jsonschema.main.{ JsonSchema, JsonSchemaFactory }
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.concurrent.duration._
import scala.util.{ Success, Try }

trait HttpDsl extends Dsl {
  this: CornichonFeature ⇒

  private val mapper = new ObjectMapper()

  sealed trait Request {
    val name: String
  }

  sealed trait WithoutPayload extends Request {
    def apply(url: String, params: (String, String)*)(implicit headers: Seq[(String, String)] = Seq.empty) =
      effectStep(
        title = {
        val base = s"$name $url"
        if (params.isEmpty) base
        else s"$base with params ${displayTuples(params)}"
      },
        effect =
        s ⇒ {
          val x = this match {
            case GET    ⇒ http.Get(url, params, headers)(s)
            case DELETE ⇒ http.Delete(url, params, headers)(s)
          }
          x.map { case (_, session) ⇒ session }.fold(e ⇒ throw e, identity)
        }
      )
  }

  sealed trait WithPayload extends Request {
    def apply(url: String, payload: String, params: (String, String)*)(implicit headers: Seq[(String, String)] = Seq.empty) =
      effectStep(
        title = {
        val base = s"$name to $url with payload $payload"
        if (params.isEmpty) base
        else s"$base with params ${displayTuples(params)}"
      },
        effect =
        s ⇒ {
          val x = this match {
            case POST ⇒ http.Post(url, payload, params, headers)(s)
            case PUT  ⇒ http.Put(url, payload, params, headers)(s)
          }
          x.map { case (_, session) ⇒ session }.fold(e ⇒ throw e, identity)
        }
      )
  }

  sealed trait Streamed extends Request {
    def apply(url: String, takeWithin: FiniteDuration, params: (String, String)*)(implicit headers: Seq[(String, String)] = Seq.empty) =
      effectStep(
        title = {
        val base = s"$name $url"
        if (params.isEmpty) base
        else s"$base with params ${displayTuples(params)}"
      },
        effect =
        s ⇒ {
          val x = this match {
            case GET_SSE ⇒ http.GetSSE(url, takeWithin, params, headers)(s)
            case GET_WS  ⇒ http.GetWS(url, takeWithin, params, headers)(s)
          }
          x.map { case (source, session) ⇒ session }.fold(e ⇒ throw e, identity)
        }
      )
  }

  case object GET extends WithoutPayload {
    val name = "GET"
  }

  case object DELETE extends WithoutPayload {
    val name = "DELETE"
  }

  case object POST extends WithPayload {
    val name = "POST"
  }

  case object PUT extends WithPayload {
    val name = "PUT"
  }

  case object GET_SSE extends Streamed {
    val name = "GET SSE"
  }

  case object GET_WS extends Streamed {
    val name = "GET WS"
  }

  def status_is(status: Int) =
    effectStep(
      title = s"status is '$status'",
      effect = s ⇒ {
      val sessionStatus = s.get(http.LastResponseStatusKey)
      // perform early check outside of engine to return the body as hint.
      if (sessionStatus != status.toString) throw new StatusError(status, sessionStatus.toInt, s.get(http.LastResponseBodyKey))
      s
    }
    )

  def headers_contain(headers: (String, String)*) =
    transform_assert_session(
      key = http.LastResponseHeadersKey,
      expected = s ⇒ true,
      (session, sessionHeaders) ⇒ {
        val sessionHeadersValue = sessionHeaders.split(",")
        headers.forall { case (name, value) ⇒ sessionHeadersValue.contains(s"$name${http.HeadersKeyValueDelim}$value") }
      }, title = s"headers contain ${headers.mkString(", ")}"
    )

  def body_is[A](mapFct: JValue ⇒ JValue, expected: A) =
    transform_assert_session(
      key = http.LastResponseBodyKey,
      expected = s ⇒ resolveAndParse(expected, s),
      (s, sessionValue) ⇒ mapFct(parseJson(sessionValue)),
      title = s"response body with transformation is '$expected'"
    )

  def body_is(whiteList: Boolean = false, expected: String): ExecutableStep[JValue] = {
    transform_assert_session(
      key = http.LastResponseBodyKey,
      title = s"response body is '$expected' with whiteList=$whiteList",
      expected = s ⇒ resolveAndParse(expected, s),
      mapValue =
      (session, sessionValue) ⇒ {
        val expectedJson = resolveAndParse(expected, session)
        val sessionValueJson = parseJson(sessionValue)
        if (whiteList) {
          val Diff(changed, _, deleted) = expectedJson.diff(sessionValueJson)
          if (deleted != JNothing) throw new WhileListError(s"White list error - '$deleted' is not defined in object '$sessionValueJson")
          if (changed != JNothing) changed else expectedJson
        } else sessionValueJson
      }
    )
  }

  def body_is(expected: String, ignoring: String*): ExecutableStep[JValue] =
    transform_assert_session(
      key = http.LastResponseBodyKey,
      title = titleBuilder(s"response body is '$expected'", ignoring),
      expected = s ⇒ resolveAndParse(expected, s),
      mapValue =
        (session, sessionValue) ⇒ {
          val jsonSessionValue = parseJson(sessionValue)
          if (ignoring.isEmpty) jsonSessionValue
          else filterJsonKeys(jsonSessionValue, ignoring)
        }
    )

  def body_is[A](ordered: Boolean, expected: A, ignoring: String*): ExecutableStep[Iterable[JValue]] =
    if (ordered)
      body_array_transform(_.arr.map(filterJsonKeys(_, ignoring)), titleBuilder(s"response body is '$expected'", ignoring), s ⇒ {
        resolveAndParse(expected, s) match {
          case expectedArray: JArray ⇒ expectedArray.arr
          case _                     ⇒ throw new NotAnArrayError(expected)
        }
      })
    else
      body_array_transform(s ⇒ s.arr.map(filterJsonKeys(_, ignoring)).toSet, titleBuilder(s"response body array not ordered is '$expected'", ignoring), s ⇒ {
        resolveAndParse(expected, s) match {
          case expectedArray: JArray ⇒ expectedArray.arr.toSet
          case _                     ⇒ throw new NotAnArrayError(expected)
        }
      })

  private def filterJsonKeys(input: JValue, keys: Seq[String]): JValue =
    keys.foldLeft(input)((j, k) ⇒ j.removeField(_._1 == k))

  def save_from_body(extractor: JValue ⇒ JValue, target: String) =
    save_from_session(http.LastResponseBodyKey, s ⇒ extractor(parseJson(s)).values.toString, target)

  def save_from_body(args: (JValue ⇒ JValue, String)*) = {
    val inputs = args.map {
      case (e, t) ⇒ FromSessionSetter(http.LastResponseBodyKey, s ⇒ e(parseJson(s)).values.toString, t)
    }
    save_from_session(inputs)
  }

  def save_body_key(rootKey: String, target: String) =
    save_from_session(http.LastResponseBodyKey, s ⇒ (parseJson(s) \ rootKey).values.toString, target)

  def save_body_keys(args: (String, String)*) = {
    val inputs = args.map {
      case (e, t) ⇒ FromSessionSetter(http.LastResponseBodyKey, s ⇒ (parseJson(s) \ e).values.toString, t)
    }
    save_from_session(inputs)
  }

  def show_last_status = show_session(http.LastResponseStatusKey)

  def show_last_response_body = show_session(http.LastResponseBodyKey)

  def show_last_response_headers = show_session(http.LastResponseHeadersKey)

  private def titleBuilder(baseTitle: String, ignoring: Seq[String]): String =
    if (ignoring.isEmpty) baseTitle
    else s"$baseTitle ignoring keys ${ignoring.map(v ⇒ s"'$v'").mkString(", ")}"

  def body_array_transform[A](mapFct: JArray ⇒ A, title: String, expected: Session ⇒ A): ExecutableStep[A] =
    transform_assert_session[A](
      title = title,
      key = http.LastResponseBodyKey,
      expected = s ⇒ expected(s),
      mapValue =
      (session, sessionValue) ⇒ {
        parseJson(sessionValue) match {
          case arr: JArray ⇒
            logger.debug(s"response_body_array_is applied to ${pretty(render(arr))}")
            mapFct(arr)
          case _ ⇒ throw new NotAnArrayError(sessionValue)
        }
      }
    )

  def response_array_size_is(size: Int) = body_array_transform(_.arr.size, s"response array size is '$size'", s ⇒ size)

  def response_array_size_is(rootKey: String, size: Int) =
    transform_assert_session(
      key = http.LastResponseBodyKey,
      expected = s ⇒ true,
      (s, sessionValue) ⇒ {
        val extracted = parseJson(sessionValue) \ rootKey
        extracted match {
          case JArray(arr) ⇒ arr.size == size
          case _           ⇒ throw new NotAnArrayError(extracted)
        }
      },
      title = s"response body '$rootKey' array size is '$size'"
    )

  def response_array_size_is(extractor: JValue ⇒ JValue, size: Int) =
    transform_assert_session(
      key = http.LastResponseBodyKey,
      expected = s ⇒ true,
      (s, sessionValue) ⇒ {
        val extracted = extractor(parseJson(sessionValue))
        extracted match {
          case JArray(arr) ⇒ arr.size == size
          case _           ⇒ throw new NotAnArrayError(extracted)
        }
      },
      title = s"response body extracted array size is '$size'"
    )

  def response_array_contains[A](element: A) = body_array_transform(_.arr.contains(parseJson(element)), s"response body array contains '$element'", s ⇒ true)

  def response_array_contains[A](rootKey: String, element: A) =
    transform_assert_session(
      key = http.LastResponseBodyKey,
      expected = s ⇒ true,
      (s, sessionValue) ⇒ {
        val extracted = parseJson(sessionValue) \ rootKey
        extracted match {
          case JArray(arr) ⇒ arr.contains(parseJson(element))
          case _           ⇒ throw new NotAnArrayError(extracted)
        }
      },
      title = s"response body '$rootKey' array contains '$element'"
    )

  def response_array_contains[A](extractor: JValue ⇒ JValue, element: A) =
    transform_assert_session(
      key = http.LastResponseBodyKey,
      expected = s ⇒ true,
      (s, sessionValue) ⇒ {
        val extracted = extractor(parseJson(sessionValue))
        extracted match {
          case JArray(arr) ⇒ arr.contains(parseJson(element))
          case _           ⇒ throw new NotAnArrayError(extracted)
        }
      },
      title = s"response body extracted array contains '$element'"
    )

  def body_against_schema(schemaUrl: String) =
    transform_assert_session(
      key = http.LastResponseBodyKey,
      expected = s ⇒ Success(true),
      title = s"response body is valid against JSON schema $schemaUrl",
      mapValue =
      (session, sessionValue) ⇒ {
        val jsonNode = mapper.readTree(sessionValue)
        Try {
          loadJsonSchemaFile(schemaUrl).validate(jsonNode).isSuccess
        }
      }
    )

  private def loadJsonSchemaFile(fileLocation: String): JsonSchema =
    JsonSchemaFactory.newBuilder().freeze().getJsonSchema(fileLocation)

  private def resolveAndParse[A](input: A, session: Session): JValue =
    parseJsonUnsafe(resolveInput(input)(session))

  def WithHeaders(headers: (String, String)*)(steps: ⇒ Unit)(implicit b: ScenarioBuilder) = {
    b.addStep {
      save(http.WithHeadersKey, headers.map { case (name, value) ⇒ s"$name${http.HeadersKeyValueDelim}$value" }.mkString(",")).copy(show = false)
    }
    steps
    b.addStep {
      remove(http.WithHeadersKey).copy(show = false)
    }
  }
}