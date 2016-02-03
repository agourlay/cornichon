package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.{ CollectionAssertionStep, AssertionStep }
import com.github.agourlay.cornichon.dsl.Dsl._
import com.github.agourlay.cornichon.http.HttpDslErrors._
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.json.{ NotAnArrayError, WhiteListError, JsonPath }
import org.json4s._

object HttpAssertions {

  case object StatusAssertion extends AssertionStep[Int, Int] {
    def is(expected: Int) = AssertStep(
      title = s"status is '$expected'",
      action = s ⇒ DetailedStepAssertion(
      expected = expected,
      result = s.get(LastResponseStatusKey).toInt,
      details = statusError(expected, s.get(LastResponseBodyKey))
    )
    )
  }

  case class SessionJsonValuesAssertion(k1: String, k2: String, private val ignoredKeys: Seq[JsonPath]) {

    def ignoring(ignoring: JsonPath*): SessionJsonValuesAssertion = copy(ignoredKeys = ignoring)

    def areEquals = AssertStep(
      title = titleBuilder(s"JSON content of key '$k1' is equal to JSON content of key '$k2'", ignoredKeys),
      action = s ⇒ {
        val v1 = removeFieldsByPath(s.getJson(k1), ignoredKeys)
        val v2 = removeFieldsByPath(s.getJson(k2), ignoredKeys)
        SimpleStepAssertion(v1, v2)
      }
    )
  }

  case class HeadersAssertion(ordered: Boolean) extends CollectionAssertionStep[(String, String), String] {
    def is(expected: (String, String)) = ???

    def sizeIs(expected: Int) = from_session_step(
      title = s"headers size is '$expected'",
      key = LastResponseHeadersKey,
      expected = s ⇒ expected,
      mapValue = (session, sessionHeaders) ⇒ sessionHeaders.split(",").size
    )

    def contains(elements: (String, String)*) = {
      from_session_step(
        title = s"headers contain ${displayTuples(elements)}",
        key = LastResponseHeadersKey,
        expected = s ⇒ true,
        mapValue = (session, sessionHeaders) ⇒ {
        val sessionHeadersValue = sessionHeaders.split(",")
        elements.forall { case (name, value) ⇒ sessionHeadersValue.contains(s"$name$HeadersKeyValueDelim$value") }
      }
      )
    }

    override def inOrder: HeadersAssertion = copy(ordered = true)
  }

  case class BodyAssertion[A](jsonPath: JsonPath, private val ignoredKeys: Seq[JsonPath], whiteList: Boolean = false, resolver: Resolver) extends AssertionStep[A, JValue] {

    def path(path: JsonPath): BodyAssertion[A] = copy(jsonPath = path)

    def ignoring(ignoring: JsonPath*): BodyAssertion[A] = copy(ignoredKeys = ignoring)

    def whiteListing: BodyAssertion[A] = copy(whiteList = true)

    override def is(expected: A): AssertStep[JValue] = {
      if (whiteList && ignoredKeys.nonEmpty)
        //TODO
        throw new RuntimeException("cant have both yadayada")
      else {
        val baseTitle = if (jsonPath.isRoot) s"response body is '$expected'" else s"response body's field '${jsonPath.pretty}' is '$expected'"
        from_session_step(
          key = LastResponseBodyKey,
          title = titleBuilder(baseTitle, ignoredKeys),
          expected = s ⇒ resolveAndParse(expected, s, resolver),
          mapValue =
            (session, sessionValue) ⇒ {
              if (whiteList) {
                val expectedJson = resolveAndParse(expected, session, resolver)
                val sessionValueJson = parseJson(sessionValue)
                val Diff(changed, _, deleted) = expectedJson.diff(sessionValueJson)
                if (deleted != JNothing) throw new WhiteListError(s"White list error - '$deleted' is not defined in object '$sessionValueJson")
                if (changed != JNothing) changed else expectedJson
              } else {
                val subJson = selectJsonPath(jsonPath, sessionValue)
                if (ignoredKeys.isEmpty) subJson
                else removeFieldsByPath(subJson, ignoredKeys)
              }
            }
        )
      }
    }
  }

  case class BodyArrayAssertion[A](jsonPath: JsonPath, ordered: Boolean, private val ignoredKeys: Seq[JsonPath], resolver: Resolver) extends CollectionAssertionStep[A, JValue] {

    def path(path: JsonPath): BodyArrayAssertion[A] = copy(jsonPath = path)

    override def inOrder: BodyArrayAssertion[A] = copy(ordered = true)

    def ignoring(ignoring: JsonPath*): BodyArrayAssertion[A] = copy(ignoredKeys = ignoring)

    override def sizeIs(size: Int): AssertStep[Int] = {
      val title = if (jsonPath.isRoot) s"response body array size is '$size'" else s"response body's array '${jsonPath.pretty}' size is '$size'"
      from_session_detail_step(
        title = title,
        key = LastResponseBodyKey,
        expected = s ⇒ size,
        mapValue = (s, sessionValue) ⇒ {
        val jArray = if (jsonPath.isRoot) parseArray(sessionValue)
        else selectArrayJsonPath(jsonPath, sessionValue)
        (jArray.arr.size, arraySizeError(size, prettyPrint(jArray)))
      }
      )
    }

    override def is(expected: A): AssertStep[Iterable[JValue]] = {
      if (ordered)
        body_array_transform(_.arr.map(removeFieldsByPath(_, ignoredKeys)), titleBuilder(s"response body is '$expected'", ignoredKeys), s ⇒ {
          resolveAndParse(expected, s, resolver) match {
            case expectedArray: JArray ⇒ expectedArray.arr
            case _                     ⇒ throw new NotAnArrayError(expected)
          }
        })
      else
        body_array_transform(s ⇒ s.arr.map(removeFieldsByPath(_, ignoredKeys)).toSet, titleBuilder(s"response body array not ordered is '$expected'", ignoredKeys), s ⇒ {
          resolveAndParse(expected, s, resolver) match {
            case expectedArray: JArray ⇒ expectedArray.arr.toSet
            case _                     ⇒ throw new NotAnArrayError(expected)
          }
        })
    }

    override def contains(elements: A*) = {
      val title = if (jsonPath.isRoot) s"response body array contains '$elements'" else s"response body's array '${jsonPath.pretty}' contains '$elements'"
      from_session_detail_step(
        title = title,
        key = LastResponseBodyKey,
        expected = s ⇒ true,
        mapValue = (s, sessionValue) ⇒ {
        val jArr = if (jsonPath.isRoot) parseArray(sessionValue)
        else selectArrayJsonPath(jsonPath, sessionValue)
        val containsAll = elements.map(parseJson).forall(jArr.arr.contains)
        (containsAll, arrayDoesNotContainError(elements.toString, prettyPrint(jArr)))
      }
      )
    }
  }

  private def body_array_transform[A](mapFct: JArray ⇒ A, title: String, expected: Session ⇒ A): AssertStep[A] =
    from_session_step[A](
      title = title,
      key = LastResponseBodyKey,
      expected = s ⇒ expected(s),
      mapValue = (session, sessionValue) ⇒ mapFct(parseArray(sessionValue))
    )

  private def titleBuilder(baseTitle: String, ignoring: Seq[JsonPath]): String =
    if (ignoring.isEmpty) baseTitle
    else s"$baseTitle ignoring keys ${ignoring.map(v ⇒ s"'${v.pretty}'").mkString(", ")}"

  private def resolveAndParse[A](input: A, session: Session, resolver: Resolver): JValue =
    parseJsonUnsafe {
      input match {
        case string: String ⇒ resolver.fillPlaceholdersUnsafe(string)(session).asInstanceOf[A]
        case _              ⇒ input
      }
    }
}
