package com.github.agourlay.cornichon.json

import cats.Show
import com.github.agourlay.cornichon.core.Session
import com.github.agourlay.cornichon.dsl.Dsl._
import com.github.agourlay.cornichon.json.JsonAssertionErrors._
import com.github.agourlay.cornichon.json.JsonDiff.Diff
import com.github.agourlay.cornichon.resolver.Resolver
import com.github.agourlay.cornichon.steps.regular.{ AssertStep, GenericAssertion }
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.util.ShowInstances._
import io.circe.Json

object JsonAssertions {

  case class JsonValuesAssertion(
      private val k1: String,
      private val k2: String,
      private val resolver: Resolver,
      private val ignoredKeys: Seq[String] = Seq.empty
  ) {

    def ignoring(ignoring: String*): JsonValuesAssertion = copy(ignoredKeys = ignoring)

    def areEquals = AssertStep(
      title = jsonAssertionTitleBuilder(s"JSON content of key '$k1' is equal to JSON content of key '$k2'", ignoredKeys),
      action = s ⇒ {
        val ignoredPaths = ignoredKeys.map(resolveParseJsonPath(_, resolver)(s))
        val v1 = removeFieldsByPath(s.getJson(k1), ignoredPaths)
        val v2 = removeFieldsByPath(s.getJson(k2), ignoredPaths)
        GenericAssertion(v1, v2)
      }
    )
  }

  case class JsonAssertion(
      private val resolver: Resolver,
      private val sessionKey: String,
      private val prettySessionKeyTitle: Option[String] = None,
      private val jsonPath: String = JsonPath.root,
      private val ignoredKeys: Seq[String] = Seq.empty,
      private val whitelist: Boolean = false
  ) {

    private val target = prettySessionKeyTitle.getOrElse(sessionKey)

    def path(path: String): JsonAssertion = copy(jsonPath = path)

    def ignoring(ignoring: String*): JsonAssertion = copy(ignoredKeys = ignoring)

    def whitelisting: JsonAssertion = copy(whitelist = true)

    def is[A: Show](expected: A): AssertStep[Json] = {
      if (whitelist && ignoredKeys.nonEmpty)
        throw InvalidIgnoringConfigError
      else {
        val baseTitle = if (jsonPath == JsonPath.root) s"$target is $expected" else s"$target's field '$jsonPath' is $expected"
        from_session_step(
          key = sessionKey,
          title = jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist),
          expected = s ⇒ resolveParseJson(expected, s, resolver),
          mapValue =
            (session, sessionValue) ⇒ {
              if (whitelist) {
                val expectedJson = resolveParseJson(expected, session, resolver)
                val sessionValueJson = resolveRunJsonPath(jsonPath, sessionValue, resolver)(session)
                val Diff(changed, _, deleted) = diff(expectedJson, sessionValueJson)
                if (deleted != Json.Null) throw WhitelistingError(elementNotDefined = prettyPrint(deleted), source = prettyPrint(sessionValueJson))
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

    def containsString(expectedPart: String): AssertStep[Boolean] = {
      val baseTitle = if (jsonPath == JsonPath.root) s"$target contains '$expectedPart'" else s"$target's field '$jsonPath' contains '$expectedPart'"
      from_session_detail_step(
        key = sessionKey,
        title = jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist),
        expected = s ⇒ true,
        mapValue =
          (session, sessionValue) ⇒ {
            val subJson = resolveRunJsonPath(jsonPath, sessionValue, resolver)(session)
            val predicate = prettyPrint(subJson).contains(expectedPart)
            (predicate, notContainedError(expectedPart, prettyPrint(subJson)))
          }
      )
    }

    def isAbsent: AssertStep[Boolean] = {
      val baseTitle = if (jsonPath == JsonPath.root) s"$target is absent" else s"$target's field '$jsonPath' is absent"
      from_session_detail_step(
        key = sessionKey,
        title = jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist),
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
      val baseTitle = if (jsonPath == JsonPath.root) s"$target is present" else s"$target's field '$jsonPath' is present"
      from_session_detail_step(
        key = sessionKey,
        title = jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist),
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
        JsonArrayAssertion(sessionKey, jsonPath, ordered = false, ignoredKeys, resolver, prettySessionKeyTitle)
  }

  case class JsonArrayAssertion(
      private val sessionKey: String,
      private val jsonPath: String,
      private val ordered: Boolean,
      private val ignoredEachKeys: Seq[String],
      private val resolver: Resolver,
      private val prettySessionKeyTitle: Option[String] = None
  ) {

    private val target = prettySessionKeyTitle.getOrElse(sessionKey)

    def inOrder = copy(ordered = true)

    def ignoringEach(ignoringEach: String*): JsonArrayAssertion = copy(ignoredEachKeys = ignoringEach)

    def isEmpty = hasSize(0)

    def hasSize(size: Int): AssertStep[Int] = {
      val title = if (jsonPath == JsonPath.root) s"$target array size is '$size'" else s"$target's array '$jsonPath' size is '$size'"
      from_session_detail_step(
        title = title,
        key = sessionKey,
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

    def is[A: Show](expected: A): AssertStep[Iterable[Json]] = {
      val assertionTitle = {
        val expectedSentence = if (ordered) s"in order is $expected" else s"is $expected"
        val titleString = if (jsonPath == JsonPath.root)
          s"$target array $expectedSentence"
        else
          s"$target's array '$jsonPath' $expectedSentence"
        jsonAssertionTitleBuilder(titleString, ignoredEachKeys)
      }

      def removeIgnoredPathFromElements(s: Session, jArray: List[Json]) = {
        val ignoredPaths = ignoredEachKeys.map(resolveParseJsonPath(_, resolver)(s))
        jArray.map(removeFieldsByPath(_, ignoredPaths))
      }

      def removeIgnoredPathFromElementsSet(s: Session, jArray: List[Json]) = removeIgnoredPathFromElements(s, jArray).toSet

      //TODO remove duplication between Array and Set base comparation
      if (ordered)
        body_array_transform(
          sessionKey = sessionKey,
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
          sessionKey = sessionKey,
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

    def not_contains[A: Show](elements: A*) = {
      val prettyElements = elements.mkString(" and ")
      val title = if (jsonPath == JsonPath.root) s"$target array does not contain $prettyElements" else s"$target's array '$jsonPath' does not contain $prettyElements"
      bodyContainsElmt(title, elements, expected = false)
    }

    def contains[A: Show](elements: A*) = {
      val prettyElements = elements.mkString(" and ")
      val title = if (jsonPath == JsonPath.root) s"$target array contains $prettyElements" else s"$target's array '$jsonPath' contains $prettyElements"
      bodyContainsElmt(title, elements, expected = true)
    }

    private def bodyContainsElmt[A: Show](title: String, elements: Seq[A], expected: Boolean): AssertStep[Boolean] = {
      from_session_detail_step(
        title = title,
        key = sessionKey,
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

  private def body_array_transform[A: Show](
    sessionKey: String,
    arrayExtractor: (Session, String) ⇒ List[Json],
    mapFct: (Session, List[Json]) ⇒ A,
    title: String,
    expected: Session ⇒ A
  ): AssertStep[A] =
    from_session_step[A](
      title = title,
      key = sessionKey,
      expected = s ⇒ expected(s),
      mapValue = (session, sessionValue) ⇒ {
      val array = arrayExtractor(session, sessionValue)
      mapFct(session, array)
    }
    )

  private def jsonAssertionTitleBuilder(baseTitle: String, ignoring: Seq[String], withWhiteListing: Boolean = false): String = {
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
