package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.core.{ Resolver, Session, DetailedStepAssertion, RunnableStep }
import com.github.agourlay.cornichon.dsl.{ CollectionAssertionStep, AssertionStep }
import com.github.agourlay.cornichon.dsl.Dsl._
import com.github.agourlay.cornichon.http.HttpDslErrors._
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.json.{ NotAnArrayError, WhiteListError, JsonPath }
import org.json4s._

object HttpAssertions {

  case object StatusAssertion extends AssertionStep[Int, String] {
    def is(expected: Int) = RunnableStep(
      title = s"status is '$expected'",
      action = s ⇒ {
      (s, DetailedStepAssertion(
        expected = expected.toString,
        result = s.get(LastResponseStatusKey),
        details = statusError(expected, s.get(LastResponseBodyKey))
      ))
    }
    )
  }

  case class BodyAssertion[A](jsonPath: JsonPath, private val ignoredKeys: Seq[JsonPath], whiteList: Boolean = false, resolver: Resolver) extends AssertionStep[A, JValue] {

    def ignoring(ignoring: JsonPath*): BodyAssertion[A] = copy(ignoredKeys = ignoring)

    def withWhiteList: BodyAssertion[A] = copy(whiteList = true)

    override def is(expected: A): RunnableStep[JValue] = {
      if (whiteList && ignoredKeys.nonEmpty)
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

  case class BodyArrayAssertion[A](ordered: Boolean, private val ignoredKeys: Seq[JsonPath], resolver: Resolver) extends CollectionAssertionStep[A, JValue] {

    override def inOrder: BodyArrayAssertion[A] = copy(ordered = true)

    def ignoring(ignoring: JsonPath*): BodyArrayAssertion[A] = copy(ignoredKeys = ignoring)

    override def sizeIs(size: Int): RunnableStep[Int] = sizeIs(JsonPath.root, size)

    def sizeIs(jsonPath: JsonPath, size: Int) = {
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

    override def is(expected: A): RunnableStep[Iterable[JValue]] = {
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

    override def contains(element: A): RunnableStep[Boolean] = contains(JsonPath.root, element)

    def contains(jsonPath: JsonPath, element: A) = {
      val title = if (jsonPath.isRoot) s"response body array contains '$element'" else s"response body's array '${jsonPath.pretty}' contains '$element'"
      from_session_detail_step(
        title = title,
        key = LastResponseBodyKey,
        expected = s ⇒ true,
        mapValue = (s, sessionValue) ⇒ {
        val jarr = if (jsonPath.isRoot) parseArray(sessionValue)
        else selectArrayJsonPath(jsonPath, sessionValue)
        (jarr.arr.contains(parseJson(element)), arrayDoesNotContainError(element.toString, prettyPrint(jarr)))
      }
      )
    }
  }

  private def body_array_transform[A](mapFct: JArray ⇒ A, title: String, expected: Session ⇒ A): RunnableStep[A] =
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
