package com.github.agourlay.cornichon.json

import cats.Show
import cats.syntax.show._
import cats.syntax.either._
import com.github.agourlay.cornichon.core.{ Session, SessionKey }
import com.github.agourlay.cornichon.json.JsonAssertionErrors._
import com.github.agourlay.cornichon.json.JsonDiffer.JsonDiff
import com.github.agourlay.cornichon.resolver.{ Resolvable, Resolver }
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.steps.regular.assertStep._
import com.github.agourlay.cornichon.util.Instances._
import io.circe.{ Encoder, Json }

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
        GenericEqualityAssertion(v1, v2)
      }
    )
  }

  case class JsonAssertion(
      private val resolver: Resolver,
      private val sessionKey: SessionKey,
      private val prettySessionKeyTitle: Option[String] = None,
      private val jsonPath: String = JsonPath.root,
      private val ignoredKeys: Seq[String] = Seq.empty,
      private val whitelist: Boolean = false
  ) {

    private val target = prettySessionKeyTitle.getOrElse(sessionKey.name)

    def path(path: String): JsonAssertion = copy(jsonPath = path)

    def ignoring(ignoring: String*): JsonAssertion = copy(ignoredKeys = ignoring)

    def whitelisting: JsonAssertion = copy(whitelist = true)

    def is[A: Show: Resolvable: Encoder](expected: A): AssertStep = {
      if (whitelist && ignoredKeys.nonEmpty)
        throw InvalidIgnoringConfigError
      else {
        val baseTitle = if (jsonPath == JsonPath.root) s"$target is $expected" else s"$target's field '$jsonPath' is $expected"
        AssertStep(
          title = jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist),
          action = s ⇒
            GenericEqualityAssertion.fromSession(s, sessionKey) { (session, sessionValue) ⇒
              val actualValue = {
                if (whitelist) {
                  val expectedJson = resolveParseJson(expected, session, resolver)
                  val sessionValueJson = resolveRunJsonPath(jsonPath, sessionValue, resolver)(session)
                  val JsonDiff(changed, _, deleted) = diff(expectedJson, sessionValueJson)
                  if (deleted != Json.Null) throw WhitelistingError(elementNotDefined = deleted.show, source = sessionValueJson.show)
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
              (resolveParseJson(expected, session, resolver), actualValue)
            }
        )
      }
    }

    def containsString(expectedPart: String) = {
      val baseTitle = if (jsonPath == JsonPath.root) s"$target contains '$expectedPart'" else s"$target's field '$jsonPath' contains '$expectedPart'"
      AssertStep(
        title = jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist),
        action = s ⇒ {
          val sessionValue = s.get(sessionKey)
          val subJson = resolveRunJsonPath(jsonPath, sessionValue, resolver)(s)
          StringContainsAssertion(subJson.show, expectedPart)
        }
      )
    }

    def isAbsent = {
      val baseTitle = if (jsonPath == JsonPath.root) s"$target is absent" else s"$target's field '$jsonPath' is absent"
      AssertStep(
        title = jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist),
        action = s ⇒
          CustomMessageEqualityAssertion.fromSession(s, sessionKey) { (session, sessionValue) ⇒
            val subJson = resolveRunJsonPath(jsonPath, sessionValue, resolver)(session)
            val predicate = subJson match {
              case Json.Null ⇒ true
              case _         ⇒ false
            }
            (true, predicate, keyIsPresentError(jsonPath, subJson.show))
          }
      )
    }

    def isPresent: AssertStep = {
      val baseTitle = if (jsonPath == JsonPath.root) s"$target is present" else s"$target's field '$jsonPath' is present"
      AssertStep(
        title = jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist),
        action = s ⇒
          CustomMessageEqualityAssertion.fromSession(s, sessionKey) { (session, sessionValue) ⇒
            val subJson = resolveRunJsonPath(jsonPath, sessionValue, resolver)(session)
            val predicate = subJson match {
              case Json.Null ⇒ false
              case _         ⇒ true
            }
            (true, predicate, keyIsAbsentError(jsonPath, parseJsonUnsafe(sessionValue).show))
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
      private val sessionKey: SessionKey,
      private val jsonPath: String,
      private val ordered: Boolean,
      private val ignoredEachKeys: Seq[String],
      private val resolver: Resolver,
      private val prettySessionKeyTitle: Option[String] = None
  ) {

    private val target = prettySessionKeyTitle.getOrElse(sessionKey)

    def inOrder = copy(ordered = true)

    def ignoringEach(ignoringEach: String*): JsonArrayAssertion = copy(ignoredEachKeys = ignoringEach)

    def isNotEmpty = AssertStep(
      title = if (jsonPath == JsonPath.root) s"$target array size is not empty" else s"$target's array '$jsonPath' size is not empty",
      action = s ⇒
      CustomMessageEqualityAssertion.fromSession(s, sessionKey) { (s, sessionValue) ⇒
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
          l ⇒ (true, l.nonEmpty, jsonArrayNotEmptyError(Json.fromValues(l)))
        )
      }
    )

    def isEmpty = hasSize(0)

    def hasSize(size: Int) = AssertStep(
      title = if (jsonPath == JsonPath.root) s"$target array size is '$size'" else s"$target's array '$jsonPath' size is '$size'",
      action = s ⇒
      CustomMessageEqualityAssertion.fromSession(s, sessionKey) { (s, sessionValue) ⇒
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
          l ⇒ (size, l.size, arraySizeError(size, Json.fromValues(l).show))
        )
      }
    )

    def is[A: Show: Resolvable: Encoder](expected: A) = {
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

      AssertStep(
        title = assertionTitle,
        action = s ⇒ {
        val arrayFromSession = applyPathAndFindArray(jsonPath, resolver)(s, s.get(sessionKey))
        val actualValue = removeIgnoredPathFromElements(s, arrayFromSession)
        val expectedArray = resolveParseJson(expected, s, resolver).arrayOrObject(
          throw NotAnArrayError(expected),
          values ⇒ values,
          _ ⇒ throw NotAnArrayError(expected)
        )
        if (ordered)
          GenericEqualityAssertion(expectedArray, actualValue)
        else
          CollectionsContainSameElements(expectedArray, actualValue)
      }
      )
    }

    def not_contains[A: Show: Resolvable: Encoder](elements: A*) = {
      val prettyElements = elements.mkString(" and ")
      val title = if (jsonPath == JsonPath.root) s"$target array does not contain $prettyElements" else s"$target's array '$jsonPath' does not contain $prettyElements"
      bodyContainsElmt(title, elements, expected = false)
    }

    def contains[A: Show: Resolvable: Encoder](elements: A*) = {
      val prettyElements = elements.mkString(" and ")
      val title = if (jsonPath == JsonPath.root) s"$target array contains $prettyElements" else s"$target's array '$jsonPath' contains $prettyElements"
      bodyContainsElmt(title, elements, expected = true)
    }

    private def bodyContainsElmt[A: Show: Resolvable: Encoder](title: String, elements: Seq[A], expected: Boolean) =
      AssertStep(
        title = title,
        action = s ⇒
        CustomMessageEqualityAssertion.fromSession(s, sessionKey) { (s, sessionValue) ⇒
          val jArr = applyPathAndFindArray(jsonPath, resolver)(s, sessionValue)
          val resolvedJson = elements.map(resolveParseJson(_, s, resolver))
          val containsAll = resolvedJson.forall(jArr.contains)
          (expected, containsAll, arrayContainsError(resolvedJson.map(_.show), Json.fromValues(jArr).show, expected))
        }
      )
  }

  private def applyPathAndFindArray(path: String, resolver: Resolver)(s: Session, sessionValue: String): List[Json] = {
    val jArr = if (path == JsonPath.root) parseArray(sessionValue)
    else {
      val parsedPath = resolveParseJsonPath(path, resolver)(s)
      selectArrayJsonPath(parsedPath, sessionValue)
    }
    jArr.fold(e ⇒ throw e, identity)
  }

  private def jsonAssertionTitleBuilder(baseTitle: String, ignoring: Seq[String], withWhiteListing: Boolean = false): String = {
    val baseWithWhite = if (withWhiteListing) baseTitle + " with white listing" else baseTitle
    if (ignoring.isEmpty) baseWithWhite
    else s"$baseWithWhite ignoring keys ${ignoring.mkString(", ")}"
  }

  private def resolveParseJson[A: Show: Encoder: Resolvable](input: A, session: Session, resolver: Resolver): Json = {
    val xorJson = for {
      resolved ← resolver.fillPlaceholders(input)(session)
      json ← parseJson(resolved)
    } yield json

    xorJson.fold(e ⇒ throw e, identity)
  }

  private def resolveParseJsonPath(path: String, resolver: Resolver)(s: Session) =
    resolver.fillPlaceholders(path)(s).map(JsonPath.parse).fold(e ⇒ throw e, identity)

  private def resolveRunJsonPath(path: String, source: String, resolver: Resolver)(s: Session): Json =
    resolver.fillPlaceholders(path)(s).flatMap(JsonPath.run(_, source)).fold(e ⇒ throw e, identity)

}
