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

  case class SessionJsonValuesAssertion(k1: String, k2: String, private val ignoredKeys: Seq[String], private val resolver: Resolver) {

    def ignoring(ignoring: String*): SessionJsonValuesAssertion = copy(ignoredKeys = ignoring)

    def areEquals = AssertStep(
      title = titleBuilder(s"JSON content of key '$k1' is equal to JSON content of key '$k2'", ignoredKeys),
      action = s ⇒ {
        val ignoredPaths = ignoredKeys.map(resolveParseJsonPath(_, resolver)(s))
        val v1 = removeFieldsByPath(s.getJson(k1), ignoredPaths)
        val v2 = removeFieldsByPath(s.getJson(k2), ignoredPaths)
        SimpleStepAssertion(v1, v2)
      }
    )
  }

  case class HeadersAssertion(ordered: Boolean) extends CollectionAssertionStep[(String, String), String] {
    def is(expected: (String, String)*) = from_session_step(
      title = s"headers is ${displayTuples(expected)}",
      key = LastResponseHeadersKey,
      expected = s ⇒ expected.map { case (name, value) ⇒ s"$name$HeadersKeyValueDelim$value" },
      mapValue = (session, sessionHeaders) ⇒ sessionHeaders.split(",")
    )

    def hasSize(expected: Int) = from_session_step(
      title = s"headers size is '$expected'",
      key = LastResponseHeadersKey,
      expected = s ⇒ expected,
      mapValue = (session, sessionHeaders) ⇒ sessionHeaders.split(",").length
    )

    def contain(elements: (String, String)*) = {
      from_session_detail_step(
        title = s"headers contain ${displayTuples(elements)}",
        key = LastResponseHeadersKey,
        expected = s ⇒ true,
        mapValue = (session, sessionHeaders) ⇒ {
        val sessionHeadersValue = sessionHeaders.split(",")
        val predicate = elements.forall { case (name, value) ⇒ sessionHeadersValue.contains(s"$name$HeadersKeyValueDelim$value") }
        (predicate, headersDoesNotContainError(displayTuples(elements), sessionHeaders))
      }
      )
    }

    override def inOrder: HeadersAssertion = copy(ordered = true)
  }

  case class BodyAssertion[A](private val jsonPath: String, private val ignoredKeys: Seq[String], whiteList: Boolean = false, resolver: Resolver) extends AssertionStep[A, JValue] {

    def path(path: String): BodyAssertion[A] = copy(jsonPath = path)

    def ignoring(ignoring: String*): BodyAssertion[A] = copy(ignoredKeys = ignoring)

    def whiteListing: BodyAssertion[A] = copy(whiteList = true)

    override def is(expected: A): AssertStep[JValue] = {
      if (whiteList && ignoredKeys.nonEmpty)
        throw InvalidIgnoringConfigError
      else {
        val baseTitle = if (jsonPath == JsonPath.root) s"response body is '$expected'" else s"response body's field '$jsonPath' is '$expected'"
        from_session_step(
          key = LastResponseBodyKey,
          title = titleBuilder(baseTitle, ignoredKeys, whiteList),
          expected = s ⇒ resolveParseJson(expected, s, resolver),
          mapValue =
            (session, sessionValue) ⇒ {
              if (whiteList) {
                val expectedJson = resolveParseJson(expected, session, resolver)
                val sessionValueJson = resolveRunJsonPath(jsonPath, sessionValue, resolver)(session)
                val Diff(changed, _, deleted) = expectedJson.diff(sessionValueJson)
                if (deleted != JNothing) throw new WhiteListError(s"White list error - '${prettyPrint(deleted)}' is not defined in object '${prettyPrint(sessionValueJson)}")
                if (changed != JNothing) changed else expectedJson
              } else {
                val subJson = resolveRunJsonPath(jsonPath, sessionValue, resolver)(session)
                if (ignoredKeys.isEmpty) subJson
                else {
                  val ignoredPaths = ignoredKeys.map(resolveParseJsonPath(_, resolver)(session))
                  removeFieldsByPath(subJson, ignoredPaths)
                }
              }
            }
        )
      }
    }

    def isAbsent: AssertStep[Boolean] = {
      val baseTitle = if (jsonPath == JsonPath.root) s"response body is absent" else s"response body's field '$jsonPath' is absent"
      from_session_detail_step(
        key = LastResponseBodyKey,
        title = titleBuilder(baseTitle, ignoredKeys, whiteList),
        expected = s ⇒ true,
        mapValue =
          (session, sessionValue) ⇒ {
            val subJson = resolveRunJsonPath(jsonPath, sessionValue, resolver)(session)
            val predicate = subJson match {
              case JNothing | JNull ⇒ true
              case _                ⇒ false
            }
            (predicate, keyIsPresentError(jsonPath, prettyPrint(subJson)))
          }
      )
    }

    def isPresent: AssertStep[Boolean] = {
      val baseTitle = if (jsonPath == JsonPath.root) s"response body is present" else s"response body's field '$jsonPath' is present"
      from_session_detail_step(
        key = LastResponseBodyKey,
        title = titleBuilder(baseTitle, ignoredKeys, whiteList),
        expected = s ⇒ true,
        mapValue =
          (session, sessionValue) ⇒ {
            val subJson = resolveRunJsonPath(jsonPath, sessionValue, resolver)(session)
            val predicate = subJson match {
              case JNothing | JNull ⇒ false
              case _                ⇒ true
            }
            (predicate, keyIsAbsentError(jsonPath, prettyPrint(parseJson(sessionValue))))
          }
      )
    }

    def asArray = BodyArrayAssertion[A](jsonPath, ordered = false, ignoredKeys, resolver)
  }

  case class BodyArrayAssertion[A](private val jsonPath: String, ordered: Boolean, private val ignoredKeys: Seq[String], resolver: Resolver) extends AssertionStep[A, Iterable[JValue]] {

    def path(path: String): BodyArrayAssertion[A] = copy(jsonPath = path)

    def inOrder: BodyArrayAssertion[A] = copy(ordered = true)

    def ignoring(ignoring: String*): BodyArrayAssertion[A] = copy(ignoredKeys = ignoring)

    def hasSize(size: Int): AssertStep[Int] = {
      val title = if (jsonPath == JsonPath.root) s"response body array size is '$size'" else s"response body's array '$jsonPath' size is '$size'"
      from_session_detail_step(
        title = title,
        key = LastResponseBodyKey,
        expected = s ⇒ size,
        mapValue = (s, sessionValue) ⇒ {
        val jArray = if (jsonPath == JsonPath.root) parseArray(sessionValue)
        else {
          val parsedPath = resolveParseJsonPath(jsonPath, resolver)(s)
          selectArrayJsonPath(parsedPath, sessionValue)
        }
        (jArray.arr.size, arraySizeError(size, prettyPrint(jArray)))
      }
      )
    }

    override def is(expected: A): AssertStep[Iterable[JValue]] = {
      val assertionTitle = {
        val expectedSentence = if (ordered) s"in order is '$expected'" else s"is '$expected'"
        val titleString = if (jsonPath == JsonPath.root)
          s"response body array $expectedSentence"
        else
          s"response body's array '$jsonPath' $expectedSentence"
        titleBuilder(titleString, ignoredKeys)
      }

      def removeIgnoredPathFromElements(s: Session, jArray: JArray) = {
        val ignoredPaths = ignoredKeys.map(resolveParseJsonPath(_, resolver)(s))
        jArray.arr.map(removeFieldsByPath(_, ignoredPaths))
      }

      def removeIgnoredPathFromElementsSet(s: Session, jArray: JArray) = removeIgnoredPathFromElements(s, jArray).toSet

      //TODO remove duplication between Array and Set base comparation
      if (ordered)
        body_array_transform(
          arrayExtractor = applyPathAndFindArray(jsonPath, resolver),
          mapFct = removeIgnoredPathFromElements,
          title = assertionTitle,
          expected = s ⇒ {
            resolveParseJson(expected, s, resolver) match {
              case expectedArray: JArray ⇒ expectedArray.arr
              case _                     ⇒ throw new NotAnArrayError(expected)
            }
          }
        )
      else
        body_array_transform(
          arrayExtractor = applyPathAndFindArray(jsonPath, resolver),
          mapFct = removeIgnoredPathFromElementsSet,
          title = assertionTitle,
          expected = s ⇒ {
            resolveParseJson(expected, s, resolver) match {
              case expectedArray: JArray ⇒ expectedArray.arr.toSet
              case _                     ⇒ throw new NotAnArrayError(expected)
            }
          }
        )
    }

    def contains(elements: A*) = {
      val prettyElements = elements.mkString(" and ")
      val title = if (jsonPath == JsonPath.root) s"response body array contains '$prettyElements'" else s"response body's array '$jsonPath.pretty' contains '$prettyElements'"
      from_session_detail_step(
        title = title,
        key = LastResponseBodyKey,
        expected = s ⇒ true,
        mapValue = (s, sessionValue) ⇒ {
        val jArr = applyPathAndFindArray(jsonPath, resolver)(s, sessionValue)
        val resolvedJson = elements.map(resolveParseJson(_, s, resolver))
        val containsAll = resolvedJson.forall(jArr.arr.contains)
        (containsAll, arrayDoesNotContainError(resolvedJson.map(prettyPrint), prettyPrint(jArr)))
      }
      )
    }
  }

  private def applyPathAndFindArray(path: String, resolver: Resolver)(s: Session, sessionValue: String): JArray = {
    val jArr = if (path == JsonPath.root) parseArray(sessionValue)
    else {
      val parsedPath = resolveParseJsonPath(path, resolver)(s)
      selectArrayJsonPath(parsedPath, sessionValue)
    }
    jArr
  }

  private def body_array_transform[A](arrayExtractor: (Session, String) ⇒ JArray, mapFct: (Session, JArray) ⇒ A, title: String, expected: Session ⇒ A): AssertStep[A] =
    from_session_step[A](
      title = title,
      key = LastResponseBodyKey,
      expected = s ⇒ expected(s),
      mapValue = (session, sessionValue) ⇒ {
      val array = arrayExtractor(session, sessionValue)
      mapFct(session, array)
    }
    )

  private def titleBuilder(baseTitle: String, ignoring: Seq[String], withWhiteListing: Boolean = false): String = {
    val baseWithWhite = if (withWhiteListing) baseTitle + " with white listing" else baseTitle

    if (ignoring.isEmpty) baseWithWhite
    else s"$baseWithWhite ignoring keys ${ignoring.mkString(", ")}"
  }

  private def resolveParseJson[A](input: A, session: Session, resolver: Resolver): JValue =
    parseJsonUnsafe {
      input match {
        case string: String ⇒ resolver.fillPlaceholdersUnsafe(string)(session).asInstanceOf[A]
        case _              ⇒ input
      }
    }

  private def resolveParseJsonPath(path: String, resolver: Resolver)(s: Session): JsonPath = {
    val resolvedPath = resolver.fillPlaceholdersUnsafe(path)(s)
    JsonPath.parse(resolvedPath)
  }

  private def resolveRunJsonPath(path: String, source: String, resolver: Resolver)(s: Session): JValue = {
    val resolvedPath = resolver.fillPlaceholdersUnsafe(path)(s)
    JsonPath.run(resolvedPath, source)
  }
}
