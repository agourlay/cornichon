package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core.RunnableStep._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.{ BodyElementCollector, Dsl }
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.json.{ NotAnArrayError, WhiteListError, JsonPath }
import org.json4s._

import scala.concurrent.duration._

trait HttpDsl extends Dsl {
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
    from_session_step(
      key = LastResponseHeadersKey,
      expected = s ⇒ true,
      (session, sessionHeaders) ⇒ {
        val sessionHeadersValue = sessionHeaders.split(",")
        headers.forall { case (name, value) ⇒ sessionHeadersValue.contains(s"$name$HeadersKeyValueDelim$value") }
      }, title = s"headers contain ${headers.mkString(", ")}"
    )

  def body_json_path_is[A](jsonPath: String, expected: A, ignoring: String*) =
    from_session_step(
      key = LastResponseBodyKey,
      expected = s ⇒ resolveAndParse(expected, s),
      (s, sessionValue) ⇒ {
        val mapped = selectJsonPath(jsonPath, sessionValue)
        if (ignoring.isEmpty) mapped
        else removeFieldsByPath(mapped, ignoring)
      },
      title = s"response body's field '$jsonPath' is '$expected'"
    )

  def body_is[A](expected: A, ignoring: String*) =
    from_session_step(
      key = LastResponseBodyKey,
      title = titleBuilder(s"response body is '$expected'", ignoring),
      expected = s ⇒ resolveAndParse(expected, s),
      mapValue =
        (session, sessionValue) ⇒ {
          val jsonSessionValue = parseJson(sessionValue)
          if (ignoring.isEmpty) jsonSessionValue
          else removeFieldsByPath(jsonSessionValue, ignoring)
        }
    )

  def body_is(whiteList: Boolean = false, expected: String): RunnableStep[JValue] = {
    from_session_step(
      key = LastResponseBodyKey,
      title = s"response body is '$expected' with whiteList=$whiteList",
      expected = s ⇒ resolveAndParse(expected, s),
      mapValue =
      (session, sessionValue) ⇒ {
        val expectedJson = resolveAndParse(expected, session)
        val sessionValueJson = parseJson(sessionValue)
        if (whiteList) {
          val Diff(changed, _, deleted) = expectedJson.diff(sessionValueJson)
          if (deleted != JNothing) throw new WhiteListError(s"White list error - '$deleted' is not defined in object '$sessionValueJson")
          if (changed != JNothing) changed else expectedJson
        } else sessionValueJson
      }
    )
  }

  def body_is[A](ordered: Boolean, expected: A, ignoring: String*): RunnableStep[Iterable[JValue]] =
    if (ordered)
      body_array_transform(_.arr.map(removeFieldsByPath(_, ignoring)), titleBuilder(s"response body is '$expected'", ignoring), s ⇒ {
        resolveAndParse(expected, s) match {
          case expectedArray: JArray ⇒ expectedArray.arr
          case _                     ⇒ throw new NotAnArrayError(expected)
        }
      })
    else
      body_array_transform(s ⇒ s.arr.map(removeFieldsByPath(_, ignoring)).toSet, titleBuilder(s"response body array not ordered is '$expected'", ignoring), s ⇒ {
        resolveAndParse(expected, s) match {
          case expectedArray: JArray ⇒ expectedArray.arr.toSet
          case _                     ⇒ throw new NotAnArrayError(expected)
        }
      })

  def save_from_body(jsonPath: String, target: String) =
    save_from_session(LastResponseBodyKey, s ⇒ selectJsonPath(jsonPath, s).values.toString, target)

  def save_from_body(args: (String, String)*) = {
    val inputs = args.map {
      case (path, t) ⇒ FromSessionSetter(LastResponseBodyKey, s ⇒ selectJsonPath(path, s).values.toString, t)
    }
    save_from_session(inputs)
  }

  def save_body_key(jsonPath: String, target: String) =
    save_from_session(LastResponseBodyKey, s ⇒ selectJsonPath(jsonPath, s).values.toString, target)

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
    from_session_step[A](
      title = title,
      key = LastResponseBodyKey,
      expected = s ⇒ expected(s),
      mapValue =
      (session, sessionValue) ⇒ {
        val jarr = parseArray(sessionValue)
        mapFct(jarr)
      }
    )

  def body_array_size_is(size: Int): RunnableStep[Int] = body_array_size_is(JsonPath.root, size)

  def body_array_size_is(jsonPath: String, size: Int) = {
    val title = if (jsonPath == JsonPath.root) s"response body array size is '$size'" else s"response body's array '$jsonPath' size is '$size'"
    from_session_detail_step(
      title = title,
      key = LastResponseBodyKey,
      expected = s ⇒ size,
      mapValue = (s, sessionValue) ⇒ {
      val jarr = if (jsonPath == JsonPath.root) parseArray(sessionValue)
      else selectArrayWithJsonPath(jsonPath, sessionValue)
      (jarr.arr.size, HttpDslError.arraySizeError(size, prettyPrint(jarr)))
    }
    )
  }

  def body_array_contains[A](element: A): RunnableStep[Boolean] = body_array_contains(JsonPath.root, element)

  def body_array_contains[A](jsonPath: String, element: A) = {
    val title = if (jsonPath == JsonPath.root) s"response body array contains '$element'" else s"response body's array '$jsonPath' contains '$element'"
    from_session_detail_step(
      title = title,
      key = LastResponseBodyKey,
      expected = s ⇒ true,
      mapValue = (s, sessionValue) ⇒ {
      val jarr = if (jsonPath == JsonPath.root) parseArray(sessionValue)
      else selectArrayWithJsonPath(jsonPath, sessionValue)
      (jarr.arr.contains(parseJson(element)), HttpDslError.arrayDoesNotContainError(element.toString, prettyPrint(jarr)))
    }
    )
  }

  private def selectArrayWithJsonPath(path: String, sessionValue: String): JArray = {
    val extracted = selectJsonPath(path, sessionValue)
    extracted match {
      case jarr: JArray ⇒ jarr
      case _            ⇒ throw new NotAnArrayError(extracted)
    }
  }

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

  def json_equality_for(k1: String, k2: String) = RunnableStep(
    title = s"JSON content of key '$k1' is equal to JSON content of key '$k2'",
    action = s ⇒ (s, SimpleStepAssertion(s.getJson(k1), s.getJson(k2)))
  )

  private object HttpDslError {
    def statusError(expected: Int, body: String): String ⇒ String = actual ⇒ {
      s"""expected '$expected' but actual is '$actual' with response body:
            |${prettyPrint(parseJson(body))}""".stripMargin
    }

    def arraySizeError(expected: Int, sourceArray: String): Int ⇒ String = actual ⇒ {
      s"""expected array size '$expected' but actual is '$actual' with array:
          |$sourceArray""".stripMargin
    }

    def arrayDoesNotContainError(expected: String, sourceArray: String): Boolean ⇒ String = resFalse ⇒ {
      s"""expected array to contain '$expected' but it is not the case with array:
          |$sourceArray""".stripMargin
    }
  }
}