package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core.RunnableStep._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.{ BodyElementCollector, Dsl }
import com.github.agourlay.cornichon.json.CornichonJson
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.concurrent.duration._

trait HttpDsl extends Dsl with CornichonJson {
  this: CornichonFeature ⇒

  import HttpService._

  sealed trait Request {
    val name: String
  }

  sealed trait WithoutPayload extends Request {
    def apply(url: String, params: (String, String)*)(implicit headers: Seq[(String, String)] = Seq.empty) =
      effectful(
        title = {
        val base = s"$name $url"
        if (params.isEmpty) base
        else s"$base with params ${displayTuples(params)}"
      },
        effect =
        s ⇒ this match {
          case GET    ⇒ http.Get(url, params, headers)(s)
          case DELETE ⇒ http.Delete(url, params, headers)(s)
        }
      )
  }

  sealed trait WithPayload extends Request {
    def apply(url: String, payload: String, params: (String, String)*)(implicit headers: Seq[(String, String)] = Seq.empty) =
      effectful(
        title = {
        val base = s"$name to $url with payload $payload"
        if (params.isEmpty) base
        else s"$base with params ${displayTuples(params)}"
      },
        effect =
        s ⇒ this match {
          case POST ⇒ http.Post(url, payload, params, headers)(s)
          case PUT  ⇒ http.Put(url, payload, params, headers)(s)
        }
      )
  }

  sealed trait Streamed extends Request {
    def apply(url: String, takeWithin: FiniteDuration, params: (String, String)*)(implicit headers: Seq[(String, String)] = Seq.empty) =
      effectful(
        title = {
        val base = s"$name $url"
        if (params.isEmpty) base
        else s"$base with params ${displayTuples(params)}"
      },
        effect =
        s ⇒ this match {
          case GET_SSE ⇒ http.GetSSE(url, takeWithin, params, headers)(s)
          case GET_WS  ⇒ http.GetWS(url, takeWithin, params, headers)(s)
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
    RunnableStep(
      title = s"status is '$status'",
      action = s ⇒ {
      (s, DetailedStepAssertion(
        expected = status.toString,
        result = s.get(LastResponseStatusKey),
        HttpDslError.statusError(status, s.get(LastResponseBodyKey))
      ))
    }
    )

  def headers_contain(headers: (String, String)*) =
    transform_assert_session(
      key = LastResponseHeadersKey,
      expected = s ⇒ true,
      (session, sessionHeaders) ⇒ {
        val sessionHeadersValue = sessionHeaders.split(",")
        headers.forall { case (name, value) ⇒ sessionHeadersValue.contains(s"$name$HeadersKeyValueDelim$value") }
      }, title = s"headers contain ${headers.mkString(", ")}"
    )

  def body_field_is[A](jsonPath: String, expected: A, ignoring: String*) =
    transform_assert_session(
      key = LastResponseBodyKey,
      expected = s ⇒ resolveAndParse(expected, s),
      (s, sessionValue) ⇒ {
        val mapped = selectJsonPath(jsonPath, parseJson(sessionValue))
        if (ignoring.isEmpty) mapped
        else filterJsonRootKeys(mapped, ignoring)
      },
      title = s"response body's field '$jsonPath' is '$expected'"
    )

  def body_is[A](expected: A, ignoring: String*) =
    transform_assert_session(
      key = LastResponseBodyKey,
      title = titleBuilder(s"response body is '$expected'", ignoring),
      expected = s ⇒ resolveAndParse(expected, s),
      mapValue =
        (session, sessionValue) ⇒ {
          val jsonSessionValue = parseJson(sessionValue)
          if (ignoring.isEmpty) jsonSessionValue
          else filterJsonRootKeys(jsonSessionValue, ignoring)
        }
    )

  def body_is(whiteList: Boolean = false, expected: String): RunnableStep[JValue] = {
    transform_assert_session(
      key = LastResponseBodyKey,
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

  def body_is[A](ordered: Boolean, expected: A, ignoring: String*): RunnableStep[Iterable[JValue]] =
    if (ordered)
      body_array_transform(_.arr.map(filterJsonRootKeys(_, ignoring)), titleBuilder(s"response body is '$expected'", ignoring), s ⇒ {
        resolveAndParse(expected, s) match {
          case expectedArray: JArray ⇒ expectedArray.arr
          case _                     ⇒ throw new NotAnArrayError(expected)
        }
      })
    else
      body_array_transform(s ⇒ s.arr.map(filterJsonRootKeys(_, ignoring)).toSet, titleBuilder(s"response body array not ordered is '$expected'", ignoring), s ⇒ {
        resolveAndParse(expected, s) match {
          case expectedArray: JArray ⇒ expectedArray.arr.toSet
          case _                     ⇒ throw new NotAnArrayError(expected)
        }
      })

  def save_from_body(jsonPath: String, target: String) =
    save_from_session(LastResponseBodyKey, s ⇒ selectJsonPath(jsonPath, parseJson(s)).values.toString, target)

  def save_from_body(args: (String, String)*) = {
    val inputs = args.map {
      case (path, t) ⇒ FromSessionSetter(LastResponseBodyKey, s ⇒ selectJsonPath(path, parseJson(s)).values.toString, t)
    }
    save_from_session(inputs)
  }

  def save_body_key(jsonPath: String, target: String) =
    save_from_session(LastResponseBodyKey, s ⇒ selectJsonPath(jsonPath, parseJson(s)).values.toString, target)

  def save_body_keys(args: (String, String)*) = {
    val inputs = args.map {
      case (e, t) ⇒ FromSessionSetter(LastResponseBodyKey, s ⇒ (parseJson(s) \ e).values.toString, t)
    }
    save_from_session(inputs)
  }

  def show_last_status = show_session(LastResponseStatusKey)

  def show_last_response_body = show_session(LastResponseBodyKey)

  def show_last_response_headers = show_session(LastResponseHeadersKey)

  private def titleBuilder(baseTitle: String, ignoring: Seq[String]): String =
    if (ignoring.isEmpty) baseTitle
    else s"$baseTitle ignoring keys ${ignoring.map(v ⇒ s"'$v'").mkString(", ")}"

  def body_array_transform[A](mapFct: JArray ⇒ A, title: String, expected: Session ⇒ A): RunnableStep[A] =
    transform_assert_session[A](
      title = title,
      key = LastResponseBodyKey,
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

  def body_array_size_is(size: Int) = body_array_transform(_.arr.size, s"response body array size is '$size'", s ⇒ size)

  def body_array_size_is(jsonPath: String, size: Int) =
    transform_assert_session(
      key = LastResponseBodyKey,
      expected = s ⇒ true,
      (s, sessionValue) ⇒ {
        val extracted = selectJsonPath(jsonPath, parseJson(sessionValue))
        extracted match {
          case JArray(arr) ⇒ arr.size == size
          case _           ⇒ throw new NotAnArrayError(extracted)
        }
      },
      title = s"response body's array '$jsonPath' size is '$size'"
    )

  def body_array_contains[A](element: A) = body_array_transform(_.arr.contains(parseJson(element)), s"response body array contains '$element'", s ⇒ true)

  def body_array_contains[A](jsonPath: String, element: A) =
    transform_assert_session(
      key = LastResponseBodyKey,
      expected = s ⇒ true,
      (s, sessionValue) ⇒ {
        val extracted = selectJsonPath(jsonPath, parseJson(sessionValue))
        extracted match {
          case JArray(arr) ⇒ arr.contains(parseJson(element))
          case _           ⇒ throw new NotAnArrayError(extracted)
        }
      },
      title = s"response body's array '$jsonPath' contains '$element'"
    )

  private def resolveAndParse[A](input: A, session: Session): JValue =
    parseJsonUnsafe {
      input match {
        case string: String ⇒ resolver.fillPlaceholdersUnsafe(string)(session).asInstanceOf[A]
        case _              ⇒ input
      }
    }

  def WithHeaders(headers: (String, String)*) =
    BodyElementCollector[Step, Seq[Step]] { steps ⇒
      val saveStep = save(WithHeadersKey, headers.map { case (name, value) ⇒ s"$name$HeadersKeyValueDelim$value" }.mkString(",")).copy(show = false)
      val removeStep = remove(WithHeadersKey).copy(show = false)

      saveStep +: steps :+ removeStep
    }

  private object HttpDslError {
    def statusError(expected: Int, body: String): String ⇒ String = actual ⇒ {
      s"""expected '$expected' but actual is '$actual' with response body:
            |${pretty(render(parseJson(body)))}""".stripMargin
    }
  }
}