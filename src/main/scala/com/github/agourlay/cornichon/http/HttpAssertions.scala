package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.{ AssertionSyntax, CollectionAssertionSyntax }
import com.github.agourlay.cornichon.dsl.Dsl._
import com.github.agourlay.cornichon.http.HttpDslErrors._
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.json.JsonDiff._
import com.github.agourlay.cornichon.json.{ JsonPath, NotAnArrayError, WhiteListError }
import com.github.agourlay.cornichon.resolver.Resolver
import com.github.agourlay.cornichon.steps.regular.{ AssertStep, CustomMessageAssertion, GenericAssertion }
import com.github.agourlay.cornichon.util.Formats._

import io.circe.Json

object HttpAssertions {

  case object StatusAssertion extends AssertionSyntax[Int, Int] {
    def is(expected: Int) = AssertStep(
      title = s"status is '$expected'",
      action = s ⇒ CustomMessageAssertion(
      expected = expected,
      actual = s.get(LastResponseStatusKey).toInt,
      customMessage = statusError(expected, s.get(LastResponseBodyKey))
    )
    )
  }

  case class SessionJsonValuesAssertion(
      private val k1: String,
      private val k2: String,
      private val ignoredKeys: Seq[String],
      private val resolver: Resolver
  ) {

    def ignoring(ignoring: String*): SessionJsonValuesAssertion = copy(ignoredKeys = ignoring)

    def areEquals = AssertStep(
      title = titleBuilder(s"JSON content of key '$k1' is equal to JSON content of key '$k2'", ignoredKeys),
      action = s ⇒ {
        val ignoredPaths = ignoredKeys.map(resolveParseJsonPath(_, resolver)(s))
        val v1 = removeFieldsByPath(s.getJson(k1), ignoredPaths)
        val v2 = removeFieldsByPath(s.getJson(k2), ignoredPaths)
        GenericAssertion(v1, v2)
      }
    )
  }

  case class HeadersAssertion(private val ordered: Boolean) extends CollectionAssertionSyntax[(String, String), String] {
    def is(expected: (String, String)*) = from_session_step[Iterable[String]](
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
        val sessionHeadersValue = sessionHeaders.split(InterHeadersValueDelim)
        val predicate = elements.forall { case (name, value) ⇒ sessionHeadersValue.contains(s"$name$HeadersKeyValueDelim$value") }
        (predicate, headersDoesNotContainError(displayTuples(elements), sessionHeaders))
      }
      )
    }

    override def inOrder: HeadersAssertion = copy(ordered = true)
  }

  case class BodyAssertion[A](
      private val jsonPath: String,
      private val ignoredKeys: Seq[String],
      private val whitelist: Boolean = false,
      private val resolver: Resolver
  ) extends AssertionSyntax[A, Json] {

    def path(path: String): BodyAssertion[A] = copy(jsonPath = path)

    def ignoring(ignoring: String*): BodyAssertion[A] = copy(ignoredKeys = ignoring)

    def whitelisting: BodyAssertion[A] = copy(whitelist = true)

    override def is(expected: A): AssertStep[Json] = {
      if (whitelist && ignoredKeys.nonEmpty)
        throw InvalidIgnoringConfigError
      else {
        val baseTitle = if (jsonPath == JsonPath.root) s"response body is $expected" else s"response body's field '$jsonPath' is $expected"
        from_session_step(
          key = LastResponseBodyKey,
          title = titleBuilder(baseTitle, ignoredKeys, whitelist),
          expected = s ⇒ resolveParseJson(expected, s, resolver),
          mapValue =
            (session, sessionValue) ⇒ {
              if (whitelist) {
                val expectedJson = resolveParseJson(expected, session, resolver)
                val sessionValueJson = resolveRunJsonPath(jsonPath, sessionValue, resolver)(session)
                val Diff(changed, _, deleted) = diff(expectedJson, sessionValueJson)
                if (deleted != Json.Null) throw WhiteListError(s"White list error - '${prettyPrint(deleted)}' is not defined in object '${prettyPrint(sessionValueJson)}")
                if (changed != Json.Null) changed else expectedJson
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
        title = titleBuilder(baseTitle, ignoredKeys, whitelist),
        expected = s ⇒ true,
        mapValue =
          (session, sessionValue) ⇒ {
            val subJson = resolveRunJsonPath(jsonPath, sessionValue, resolver)(session)
            val predicate = subJson match {
              case Json.Null ⇒ true
              case _         ⇒ false
            }
            (predicate, keyIsPresentError(jsonPath, prettyPrint(subJson)))
          }
      )
    }

    def isPresent: AssertStep[Boolean] = {
      val baseTitle = if (jsonPath == JsonPath.root) s"response body is present" else s"response body's field '$jsonPath' is present"
      from_session_detail_step(
        key = LastResponseBodyKey,
        title = titleBuilder(baseTitle, ignoredKeys, whitelist),
        expected = s ⇒ true,
        mapValue =
          (session, sessionValue) ⇒ {
            val subJson = resolveRunJsonPath(jsonPath, sessionValue, resolver)(session)
            val predicate = subJson match {
              case Json.Null ⇒ false
              case _         ⇒ true
            }
            (predicate, keyIsAbsentError(jsonPath, prettyPrint(parseJsonUnsafe(sessionValue))))
          }
      )
    }

    def asArray =
      if (ignoredKeys.nonEmpty)
        throw UseIgnoringEach
      else
        BodyArrayAssertion[A](jsonPath, ordered = false, ignoredKeys, resolver)
  }

  case class BodyArrayAssertion[A](
      private val jsonPath: String,
      private val ordered: Boolean,
      private val ignoredEachKeys: Seq[String],
      private val resolver: Resolver
  ) extends AssertionSyntax[A, Iterable[Json]] {

    def inOrder = copy[A](ordered = true)

    def ignoringEach(ignoringEach: String*): BodyArrayAssertion[A] = copy(ignoredEachKeys = ignoringEach)

    def isEmpty = hasSize(0)

    def hasSize(size: Int): AssertStep[Int] = {
      val title = if (jsonPath == JsonPath.root) s"response body array size is '$size'" else s"response body's array '$jsonPath' size is '$size'"
      from_session_detail_step(
        title = title,
        key = LastResponseBodyKey,
        expected = s ⇒ size,
        mapValue = (s, sessionValue) ⇒ {
        val jArray = {
          if (jsonPath == JsonPath.root)
            parseArray(sessionValue)
          else {
            val parsedPath = resolveParseJsonPath(jsonPath, resolver)(s)
            selectArrayJsonPath(parsedPath, sessionValue)
          }
        }
        jArray.fold(
          e ⇒ throw e,
          l ⇒ (l.size, arraySizeError(size, prettyPrint(Json.fromValues(l))))
        )
      }
      )
    }

    override def is(expected: A): AssertStep[Iterable[Json]] = {
      val assertionTitle = {
        val expectedSentence = if (ordered) s"in order is $expected" else s"is $expected"
        val titleString = if (jsonPath == JsonPath.root)
          s"response body array $expectedSentence"
        else
          s"response body's array '$jsonPath' $expectedSentence"
        titleBuilder(titleString, ignoredEachKeys)
      }

      def removeIgnoredPathFromElements(s: Session, jArray: List[Json]) = {
        val ignoredPaths = ignoredEachKeys.map(resolveParseJsonPath(_, resolver)(s))
        jArray.map(removeFieldsByPath(_, ignoredPaths))
      }

      def removeIgnoredPathFromElementsSet(s: Session, jArray: List[Json]) = removeIgnoredPathFromElements(s, jArray).toSet

      //TODO remove duplication between Array and Set base comparation
      if (ordered)
        body_array_transform(
          arrayExtractor = applyPathAndFindArray(jsonPath, resolver),
          mapFct = removeIgnoredPathFromElements,
          title = assertionTitle,
          expected = s ⇒ {
            resolveParseJson(expected, s, resolver).arrayOrObject(
              throw NotAnArrayError(expected),
              values ⇒ values,
              obj ⇒ throw NotAnArrayError(expected)
            )
          }
        )
      else
        body_array_transform(
          arrayExtractor = applyPathAndFindArray(jsonPath, resolver),
          mapFct = removeIgnoredPathFromElementsSet,
          title = assertionTitle,
          expected = s ⇒ {
            resolveParseJson(expected, s, resolver).arrayOrObject(
              throw NotAnArrayError(expected),
              values ⇒ values.toSet,
              obj ⇒ throw NotAnArrayError(expected)
            )
          }
        )
    }

    def not_contains(elements: A*) = {
      val prettyElements = elements.mkString(" and ")
      val title = if (jsonPath == JsonPath.root) s"response body array does not contain $prettyElements" else s"response body's array '$jsonPath' does not contain $prettyElements"
      bodyContainsElmt(title, elements, expected = false)
    }

    def contains(elements: A*) = {
      val prettyElements = elements.mkString(" and ")
      val title = if (jsonPath == JsonPath.root) s"response body array contains $prettyElements" else s"response body's array '$jsonPath' contains $prettyElements"
      bodyContainsElmt(title, elements, expected = true)
    }

    private def bodyContainsElmt(title: String, elements: Seq[A], expected: Boolean): AssertStep[Boolean] = {
      from_session_detail_step(
        title = title,
        key = LastResponseBodyKey,
        expected = s ⇒ expected,
        mapValue = (s, sessionValue) ⇒ {
        val jArr = applyPathAndFindArray(jsonPath, resolver)(s, sessionValue)
        val resolvedJson = elements.map(resolveParseJson(_, s, resolver))
        val containsAll = resolvedJson.forall(jArr.contains)
        (containsAll, arrayContainsError(resolvedJson.map(prettyPrint), prettyPrint(Json.fromValues(jArr)), expected))
      }
      )
    }
  }

  private def applyPathAndFindArray(path: String, resolver: Resolver)(s: Session, sessionValue: String): List[Json] = {
    val jArr = if (path == JsonPath.root) parseArray(sessionValue)
    else {
      val parsedPath = resolveParseJsonPath(path, resolver)(s)
      selectArrayJsonPath(parsedPath, sessionValue)
    }
    jArr.fold(e ⇒ throw e, identity)
  }

  private def body_array_transform[A](
    arrayExtractor: (Session, String) ⇒ List[Json],
    mapFct: (Session, List[Json]) ⇒ A,
    title: String,
    expected: Session ⇒ A
  ): AssertStep[A] =
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

  private def resolveParseJson[A](input: A, session: Session, resolver: Resolver): Json =
    parseJson {
      input match {
        case string: String ⇒ resolver.fillPlaceholdersUnsafe(string)(session).asInstanceOf[A]
        case _              ⇒ input
      }
    }.fold(e ⇒ throw e, identity)

  private def resolveParseJsonPath(path: String, resolver: Resolver)(s: Session) =
    resolver.fillPlaceholders(path)(s).map(JsonPath.parse).fold(e ⇒ throw e, identity)

  private def resolveRunJsonPath(path: String, source: String, resolver: Resolver)(s: Session): Json =
    resolver.fillPlaceholders(path)(s).flatMap(JsonPath.run(_, source)).fold(e ⇒ throw e, identity)

}
