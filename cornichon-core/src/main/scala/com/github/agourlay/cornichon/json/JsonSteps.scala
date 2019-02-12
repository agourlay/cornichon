package com.github.agourlay.cornichon.json

import cats.{ Order, Show }
import cats.syntax.show._
import cats.syntax.either._
import cats.syntax.traverse._
import cats.instances.boolean._
import cats.instances.int._
import cats.instances.vector._
import cats.instances.list._
import cats.instances.either._
import cats.instances.string._
import com.github.agourlay.cornichon.core.{ CornichonError, Session, SessionKey }
import com.github.agourlay.cornichon.json.JsonAssertionErrors._
import com.github.agourlay.cornichon.resolver.{ PlaceholderResolver, Resolvable }
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.matchers.{ MatcherAssertion, MatcherResolver }
import com.github.agourlay.cornichon.steps.regular.assertStep._
import io.circe.{ Decoder, Encoder, Json }

import scala.util.matching.Regex

object JsonSteps {

  case class JsonValuesStepBuilder(
      private val k1: String,
      private val k2: String,
      private val placeholderResolver: PlaceholderResolver,
      private val ignoredKeys: List[String] = Nil
  ) {

    def ignoring(ignoring: String*): JsonValuesStepBuilder = copy(ignoredKeys = ignoring.toList)

    def areEquals = AssertStep(
      title = jsonAssertionTitleBuilder(s"JSON content of key '$k1' is equal to JSON content of key '$k2'", ignoredKeys),
      action = s ⇒ Assertion.either {
        for {
          ignoredPaths ← ignoredKeys.traverse(resolveAndParseJsonPath(_, placeholderResolver)(s))
          v1 ← s.getJson(k1).map(removeFieldsByPath(_, ignoredPaths))
          v2 ← s.getJson(k2).map(removeFieldsByPath(_, ignoredPaths))
        } yield GenericEqualityAssertion(v1, v2)
      }
    )
  }

  case class JsonStepBuilder(
      private val placeholderResolver: PlaceholderResolver,
      private val matcherResolver: MatcherResolver,
      private val sessionKey: SessionKey,
      private val prettySessionKeyTitle: Option[String] = None,
      private val jsonPath: String = JsonPath.root,
      private val ignoredKeys: List[String] = Nil,
      private val whitelist: Boolean = false
  ) {

    private val target = prettySessionKeyTitle.getOrElse(sessionKey.name)

    def path(path: String): JsonStepBuilder = copy(jsonPath = path)

    def ignoring(ignoring: String*): JsonStepBuilder = copy(ignoredKeys = ignoring.toList)

    def whitelisting: JsonStepBuilder = copy(whitelist = true)

    def is[A: Show: Resolvable: Encoder](expected: Either[CornichonError, A]): AssertStep = expected match {
      case Left(e) ⇒
        val baseTitle = if (jsonPath == JsonPath.root) s"$target " else s"$target's field '$jsonPath'"
        AssertStep(jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist), _ ⇒ Assertion.either(Left(e)))
      case Right(a) ⇒
        is(a)
    }

    def is[A: Show: Resolvable: Encoder](expected: A): AssertStep = isImpl(expected)
    def isNot[A: Show: Resolvable: Encoder](expected: A): AssertStep = isImpl(expected, negate = true)

    private def isImpl[A: Show: Resolvable: Encoder](expected: A, negate: Boolean = false): AssertStep = {
      val expectedShow = expected.show
      val isOrNot = if (negate) "is not" else "is"
      val baseTitle = if (jsonPath == JsonPath.root) s"$target $isOrNot\n$expectedShow" else s"$target's field '$jsonPath' $isOrNot\n$expectedShow"

      def handleMatchers(session: Session, sessionValueWithFocusJson: Json): Either[CornichonError, (Json, Json, List[MatcherAssertion])] =
        matcherResolver.findAllMatchers(expectedShow).flatMap { matchers ⇒
          if (matchers.nonEmpty) {
            val withQuotedMatchers = Resolvable[A].transformResolvableForm(expected) { r ⇒
              // don't add quotes if is not a complex JsonObject otherwise it would produce a double quoted string
              if (isJsonString(r)) r
              else matcherResolver.quoteMatchers(r, matchers)
            }
            resolveAndParseJson(withQuotedMatchers, session, placeholderResolver).flatMap {
              expectedJson ⇒ matcherResolver.prepareMatchers(matchers, expectedJson, sessionValueWithFocusJson, negate)
            }
          } else
            resolveAndParseJson(expected, session, placeholderResolver).map {
              expectedJson ⇒ (expectedJson, sessionValueWithFocusJson, Nil)
            }
        }

      def handleIgnoredFields(s: Session, expected: Json, actual: Json) =
        if (whitelist)
          // add missing fields in the expected result
          whitelistingValue(expected, actual).map(expectedWhitelistedValue ⇒ (expectedWhitelistedValue, actual))
        else if (ignoredKeys.nonEmpty)
          // remove ignore fields from the actual result
          ignoredKeys.traverse(resolveAndParseJsonPath(_, placeholderResolver)(s))
            .map(ignoredPaths ⇒ (removeFieldsByPath(expected, ignoredPaths), removeFieldsByPath(actual, ignoredPaths)))
        else
          // nothing to prepare
          (expected, actual).asRight

      AssertStep(
        title = jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist),
        action = s ⇒ Assertion.either {
          if (whitelist && ignoredKeys.nonEmpty)
            InvalidIgnoringConfigError.asLeft
          else
            for {
              sessionValue ← s.get(sessionKey)
              sessionValueWithFocusJson ← resolveRunMandatoryJsonPath(jsonPath, sessionValue, placeholderResolver)(s)
              (expectedWithoutMatchers, actualWithoutMatchers, matcherAssertions) ← handleMatchers(s, sessionValueWithFocusJson)
              (expectedPrepared, actualPrepared) ← handleIgnoredFields(s, expectedWithoutMatchers, actualWithoutMatchers)
            } yield {
              if (negate && matcherAssertions.nonEmpty && expectedPrepared.isNull && actualPrepared.isNull)
                // Handles annoying edge case of no payload remaining once all matched keys have been removed for negated assertion
                Assertion.all(matcherAssertions)
              else
                GenericEqualityAssertion(expectedPrepared, actualPrepared, negate) andAll matcherAssertions
            }
        }
      )
    }

    def isLessThan[A: Show: Resolvable: Order: Decoder](lessThan: A): AssertStep = {
      val baseTitle = if (jsonPath == JsonPath.root) s"$target is less than '$lessThan'" else s"$target's field '$jsonPath' is less than '$lessThan'"
      AssertStep(
        title = jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist),
        action = s ⇒ Assertion.either {
          for {
            sessionValue ← s.get(sessionKey)
            subJson ← resolveRunMandatoryJsonPath(jsonPath, sessionValue, placeholderResolver)(s)
            subJsonTyped ← decodeAs[A](subJson)
            resolvedExpected ← placeholderResolver.fillPlaceholders(lessThan)(s)
          } yield LessThanAssertion(subJsonTyped, resolvedExpected)

        }
      )
    }

    def isGreaterThan[A: Show: Order: Resolvable: Decoder](greaterThan: A): AssertStep = {
      val baseTitle = if (jsonPath == JsonPath.root) s"$target is greater than '$greaterThan'" else s"$target's field '$jsonPath' is greater than '$greaterThan'"
      AssertStep(
        title = jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist),
        action = s ⇒ Assertion.either {
          for {
            sessionValue ← s.get(sessionKey)
            subJson ← resolveRunMandatoryJsonPath(jsonPath, sessionValue, placeholderResolver)(s)
            subJsonTyped ← decodeAs[A](subJson)
            resolvedExpected ← placeholderResolver.fillPlaceholders(greaterThan)(s)
          } yield GreaterThanAssertion(subJsonTyped, resolvedExpected)
        }
      )
    }

    def isBetween[A: Show: Order: Resolvable: Decoder](less: A, greater: A): AssertStep = {
      val baseTitle = if (jsonPath == JsonPath.root) s"$target is between '$less' and '$greater'" else s"$target's field '$jsonPath'  is between '$less' and '$greater'"
      AssertStep(
        title = jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist),
        action = s ⇒ Assertion.either {
          for {
            sessionValue ← s.get(sessionKey)
            subJson ← resolveRunMandatoryJsonPath(jsonPath, sessionValue, placeholderResolver)(s)
            subJsonTyped ← decodeAs[A](subJson)
            resolvedLess ← placeholderResolver.fillPlaceholders(less)(s)
            resolvedGreater ← placeholderResolver.fillPlaceholders(greater)(s)
          } yield BetweenAssertion(resolvedLess, subJsonTyped, resolvedGreater)
        }
      )
    }

    def containsString(expectedPart: String): AssertStep = {
      val baseTitle = if (jsonPath == JsonPath.root) s"$target contains '$expectedPart'" else s"$target's field '$jsonPath' contains '$expectedPart'"
      AssertStep(
        title = jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist),
        action = s ⇒ Assertion.either {
          for {
            sessionValue ← s.get(sessionKey)
            subJson ← resolveRunMandatoryJsonPath(jsonPath, sessionValue, placeholderResolver)(s)
            resolvedExpected ← placeholderResolver.fillPlaceholders(expectedPart)(s)
          } yield StringContainsAssertion(subJson.show, resolvedExpected)
        }
      )
    }

    def matchesRegex(expectedRegex: Regex): AssertStep = {
      val baseTitle = if (jsonPath == JsonPath.root) s"$target matches '$expectedRegex'" else s"$target's field '$jsonPath' matches '$expectedRegex'"
      AssertStep(
        title = jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist),
        action = s ⇒ Assertion.either {
          for {
            sessionValue ← s.get(sessionKey)
            subJson ← resolveRunMandatoryJsonPath(jsonPath, sessionValue, placeholderResolver)(s)
          } yield RegexAssertion(subJson.show, expectedRegex)
        }
      )
    }

    def isNull: AssertStep = {
      val baseTitle = if (jsonPath == JsonPath.root) s"$target is null" else s"$target's field '$jsonPath' is null"
      AssertStep(
        title = jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist),
        action = s ⇒ Assertion.either {
          for {
            sessionValue ← s.get(sessionKey)
            subJson ← resolveRunMandatoryJsonPath(jsonPath, sessionValue, placeholderResolver)(s)
          } yield GenericEqualityAssertion(subJson, Json.Null)
        }
      )
    }

    def isAbsent: AssertStep = {
      val baseTitle = if (jsonPath == JsonPath.root) s"$target is absent" else s"$target's field '$jsonPath' is absent"
      AssertStep(
        title = jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist),
        action = s ⇒ Assertion.either {
          for {
            sessionValue ← s.get(sessionKey)
            subJson ← resolveRunJsonPath(jsonPath, sessionValue, placeholderResolver)(s)
          } yield CustomMessageEqualityAssertion(true, subJson.isEmpty, () ⇒ keyIsPresentError(jsonPath, subJson.get)) //YOLO
        }
      )
    }

    def isPresent: AssertStep = {
      val baseTitle = if (jsonPath == JsonPath.root) s"$target is present" else s"$target's field '$jsonPath' is present"
      AssertStep(
        title = jsonAssertionTitleBuilder(baseTitle, ignoredKeys, whitelist),
        action = s ⇒ Assertion.either {
          for {
            sessionValue ← s.get(sessionKey)
            subJson ← resolveRunJsonPath(jsonPath, sessionValue, placeholderResolver)(s)
          } yield CustomMessageEqualityAssertion(true, subJson.isDefined, () ⇒ keyIsAbsentError(jsonPath, sessionValue))
        }
      )
    }

    def asArray: JsonArrayStepBuilder =
      if (ignoredKeys.nonEmpty)
        throw UseIgnoringEach.toException
      else
        JsonArrayStepBuilder(sessionKey, jsonPath, ordered = false, ignoredEachKeys = Nil, placeholderResolver, prettySessionKeyTitle)
  }

  case class JsonArrayStepBuilder(
      private val sessionKey: SessionKey,
      private val jsonPath: String,
      private val ordered: Boolean,
      private val ignoredEachKeys: List[String],
      private val resolver: PlaceholderResolver,
      private val prettySessionKeyTitle: Option[String] = None
  ) {

    private val target = prettySessionKeyTitle.getOrElse(sessionKey)

    def inOrder: JsonArrayStepBuilder = copy(ordered = true)

    def ignoringEach(ignoringEach: String*): JsonArrayStepBuilder = copy(ignoredEachKeys = ignoringEach.toList)

    def isNotEmpty = AssertStep(
      title = if (jsonPath == JsonPath.root) s"$target array size is not empty" else s"$target's array '$jsonPath' size is not empty",
      action = s ⇒ Assertion.either {
        for {
          sessionValue ← s.get(sessionKey)
          elements ← applyPathAndFindArray(jsonPath, resolver)(s, sessionValue)
        } yield CustomMessageEqualityAssertion(true, elements.nonEmpty, () ⇒ jsonArrayNotEmptyError(parseJsonUnsafe(sessionValue).show))
      }
    )

    def isEmpty: AssertStep = hasSize(0)

    def size: GenericAssertStepBuilder[Int] = new GenericAssertStepBuilder[Int] {
      override val baseTitle: String = if (jsonPath == JsonPath.root) s"$target array size" else s"$target's array '$jsonPath' size"

      override def sessionExtractor(s: Session): Either[CornichonError, (Int, Some[() ⇒ String])] =
        for {
          sessionValue ← s.get(sessionKey)
          elements ← applyPathAndFindArray(jsonPath, resolver)(s, sessionValue)
        } yield (elements.size, Some(() ⇒ Json.fromValues(elements).show))
    }

    def hasSize(expectedSize: Int) = AssertStep(
      title = if (jsonPath == JsonPath.root) s"$target array size is '$expectedSize'" else s"$target's array '$jsonPath' size is '$expectedSize'",
      action = s ⇒ Assertion.either {
        for {
          sessionValue ← s.get(sessionKey)
          elements ← applyPathAndFindArray(jsonPath, resolver)(s, sessionValue)
        } yield CustomMessageEqualityAssertion(expectedSize, elements.size, () ⇒ arraySizeError(expectedSize, elements))
      }
    )

    def is[A: Show: Resolvable: Encoder](expected: Either[CornichonError, A]): AssertStep = expected match {
      case Left(e) ⇒
        val baseTitle = if (jsonPath == JsonPath.root) s"$target array " else s"$target's array '$jsonPath'"
        AssertStep(jsonAssertionTitleBuilder(baseTitle, ignoredEachKeys), _ ⇒ Assertion.either(Left(e)))
      case Right(a) ⇒
        is(a)
    }

    def is[A: Show: Resolvable: Encoder](expected: A): AssertStep = {
      val assertionTitle = {
        val expectedShow = expected.show
        val expectedSentence = if (ordered) s"in order is\n$expectedShow" else s"is\n$expectedShow"
        val titleString = if (jsonPath == JsonPath.root)
          s"$target array $expectedSentence"
        else
          s"$target's array '$jsonPath' $expectedSentence"
        jsonAssertionTitleBuilder(titleString, ignoredEachKeys)
      }

      AssertStep(
        title = assertionTitle,
        action = s ⇒ Assertion.either {
          for {
            expectedArrayJson ← resolveAndParseJson(expected, s, resolver)
            expectedArray ← Either.fromOption(expectedArrayJson.asArray, NotAnArrayError(expected))
            expectedArrayWithIgnore ← removeIgnoredPathFromElements(s, expectedArray)
            sessionValue ← s.get(sessionKey)
            arrayFromSession ← applyPathAndFindArray(jsonPath, resolver)(s, sessionValue)
            actualValueWithIgnore ← removeIgnoredPathFromElements(s, arrayFromSession)
          } yield {
            if (ordered)
              GenericEqualityAssertion(expectedArrayWithIgnore, actualValueWithIgnore)
            else
              CollectionsContainSameElements(expectedArrayWithIgnore, actualValueWithIgnore)
          }
        }
      )
    }

    def not_contains[A: Show: Resolvable: Encoder](elements: A*): AssertStep = {
      val prettyElements = elements.mkString(" and ")
      val title = if (jsonPath == JsonPath.root) s"$target array does not contain\n$prettyElements" else s"$target's array '$jsonPath' does not contain\n$prettyElements"
      bodyContainsElmt(title, elements, expected = false)
    }

    def contains[A: Show: Resolvable: Encoder](elements: A*): AssertStep = {
      val prettyElements = elements.mkString(" and ")
      val title = if (jsonPath == JsonPath.root) s"$target array contains\n$prettyElements" else s"$target's array '$jsonPath' contains\n$prettyElements"
      bodyContainsElmt(title, elements, expected = true)
    }

    private def bodyContainsElmt[A: Show: Resolvable: Encoder](title: String, expectedElements: Seq[A], expected: Boolean) =
      AssertStep(
        title = title,
        action = s ⇒ Assertion.either {
          for {
            sessionValue ← s.get(sessionKey)
            jArr ← applyPathAndFindArray(jsonPath, resolver)(s, sessionValue)
            actualValue ← removeIgnoredPathFromElements(s, jArr)
            resolvedJson ← expectedElements.toVector.traverse(resolveAndParseJson(_, s, resolver))
            containsAll = resolvedJson.forall(actualValue.contains)
          } yield CustomMessageEqualityAssertion(expected, containsAll, () ⇒ arrayContainsError(resolvedJson, jArr, expected))
        }
      )

    private def removeIgnoredPathFromElements(s: Session, jArray: Vector[Json]) =
      ignoredEachKeys.traverse(resolveAndParseJsonPath(_, resolver)(s))
        .map(ignoredPaths ⇒ jArray.map(removeFieldsByPath(_, ignoredPaths)))

    private def applyPathAndFindArray(path: String, resolver: PlaceholderResolver)(s: Session, sessionValue: String): Either[CornichonError, Vector[Json]] =
      if (path == JsonPath.root)
        parseArray(sessionValue)
      else
        resolveAndParseJsonPath(path, resolver)(s).flatMap(selectMandatoryArrayJsonPath(sessionValue, _))
  }

  private def jsonAssertionTitleBuilder(baseTitle: String, ignoring: Seq[String], withWhiteListing: Boolean = false): String = {
    val baseWithWhite = if (withWhiteListing) baseTitle + " with white listing" else baseTitle
    if (ignoring.isEmpty) baseWithWhite
    else s"$baseWithWhite ignoring keys ${ignoring.mkString(", ")}"
  }

  private def resolveAndParseJson[A: Show: Encoder: Resolvable](input: A, s: Session, pr: PlaceholderResolver): Either[CornichonError, Json] =
    pr.fillPlaceholders(input)(s).flatMap(parseJson)

  private def resolveAndParseJsonPath(path: String, pr: PlaceholderResolver)(s: Session): Either[CornichonError, JsonPath] =
    pr.fillPlaceholders(path)(s).flatMap(JsonPath.parse)

  private def resolveRunJsonPath(path: String, source: String, pr: PlaceholderResolver)(s: Session): Either[CornichonError, Option[Json]] =
    resolveAndParseJsonPath(path, pr)(s).flatMap(_.run(source))

  private def resolveRunMandatoryJsonPath(path: String, source: String, pr: PlaceholderResolver)(s: Session): Either[CornichonError, Json] =
    resolveAndParseJsonPath(path, pr)(s).flatMap(_.runStrict(source))
}
