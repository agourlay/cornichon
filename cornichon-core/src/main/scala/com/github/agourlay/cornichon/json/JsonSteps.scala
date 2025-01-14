package com.github.agourlay.cornichon.json

import cats.{ Order, Show }
import cats.syntax.show._
import cats.syntax.either._
import com.github.agourlay.cornichon.core.{ CornichonError, Done, ScenarioContext, SessionKey }
import com.github.agourlay.cornichon.json.JsonAssertionErrors._
import com.github.agourlay.cornichon.resolver.Resolvable
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.matchers.{ MatcherAssertion, MatcherResolver }
import com.github.agourlay.cornichon.steps.regular.assertStep._
import com.github.agourlay.cornichon.util.StringUtils.quoteInto
import com.github.agourlay.cornichon.util.TraverseUtils.{ traverse, traverseIV }
import io.circe.{ Decoder, Encoder, Json }

import scala.util.matching.Regex

object JsonSteps {

  case class JsonValuesStepBuilder(
      private val k1: String,
      private val k2: String,
      private val ignoredKeys: List[String] = Nil,
      private val jsonPath: String = JsonPath.root
  ) {

    def ignoring(ignoring: String*): JsonValuesStepBuilder = copy(ignoredKeys = ignoring.toList)
    def path(path: String): JsonValuesStepBuilder = copy(jsonPath = path)

    private def areEqualsImpl(negate: Boolean) = {
      val prefix = if (jsonPath == JsonPath.root) s"JSON content" else s"JSON field '$jsonPath'"
      val titleBuilder = new StringBuilder()
      titleBuilder.append(prefix)
      titleBuilder.append(s" of key '$k1' is ")
      if (negate) titleBuilder.append("not ")
      titleBuilder.append(s"equal to $prefix of key '$k2'")
      AssertStep(
        title = jsonAssertionTitleBuilder(titleBuilder, ignoredKeys),
        action = sc => Assertion.either {
          for {
            ignoredPaths <- traverse(ignoredKeys)(resolveAndParseJsonPath(_, sc))
            jsonPathFocus <- resolveAndParseJsonPath(jsonPath, sc)
            v1 <- getParseFocusIgnore(k1, sc, ignoredPaths, jsonPathFocus)
            v2 <- getParseFocusIgnore(k2, sc, ignoredPaths, jsonPathFocus)
          } yield GenericEqualityAssertion(v1, v2, negate)
        }
      )
    }

    def areEquals: AssertStep = areEqualsImpl(negate = false)
    def areNotEquals: AssertStep = areEqualsImpl(negate = true)

    private def getParseFocusIgnore(k: String, context: ScenarioContext, ignoredPaths: Seq[JsonPath], jsonPath: JsonPath): Either[CornichonError, Json] =
      for {
        v <- context.session.get(k)
        vFocus <- jsonPath.runStrict(v)
      } yield removeFieldsByPath(vFocus, ignoredPaths)
  }

  case class JsonStepBuilder(
      private val sessionKey: SessionKey,
      private val prettySessionKeyTitle: Option[String] = None,
      private val jsonPath: String = JsonPath.root,
      private val ignoredKeys: List[String] = Nil,
      private val whitelist: Boolean = false
  ) {

    private val target = prettySessionKeyTitle.getOrElse(s"session key '${sessionKey.name}'")

    def path(path: String): JsonStepBuilder = copy(jsonPath = path)

    def ignoring(ignoring: String*): JsonStepBuilder = copy(ignoredKeys = ignoring.toList)

    def whitelisting: JsonStepBuilder = copy(whitelist = true)

    def is[A: Show: Resolvable: Encoder](expected: Either[CornichonError, A]): AssertStep = expected match {
      case Left(e) =>
        val titleBuilder = new StringBuilder()
        titleBuilder.append(target)
        if (jsonPath != JsonPath.root) titleBuilder.append(s"'s field '$jsonPath'")
        AssertStep(jsonAssertionTitleBuilder(titleBuilder, ignoredKeys, whitelist), _ => Assertion.either(Left(e)))
      case Right(a) =>
        is(a)
    }

    def is[A: Show: Resolvable: Encoder](expected: A): AssertStep = isImpl(expected)
    def isNot[A: Show: Resolvable: Encoder](expected: A): AssertStep = isImpl(expected, negate = true)

    private def isImpl[A: Show: Resolvable: Encoder](expected: A, negate: Boolean = false): AssertStep = {
      val expectedShow = expected.show
      val titleBuilder = new StringBuilder()
      titleBuilder.append(target)
      if (jsonPath != JsonPath.root) {
        titleBuilder.append("'s field ")
        quoteInto(titleBuilder, jsonPath)
      }
      if (negate)
        titleBuilder.append(" is not")
      else
        titleBuilder.append(" is")
      titleBuilder.append("\n")
      titleBuilder.append(expectedShow)

      def handleIgnoredFields(sc: ScenarioContext, expected: Json, actual: Json) =
        if (whitelist)
          // add missing fields in the expected result
          whitelistingValue(expected, actual).map(expectedWhitelistedValue => (expectedWhitelistedValue, actual))
        else if (ignoredKeys.nonEmpty)
          // remove ignore fields from the actual result
          traverse(ignoredKeys)(resolveAndParseJsonPath(_, sc))
            .map(ignoredPaths => (removeFieldsByPath(expected, ignoredPaths), removeFieldsByPath(actual, ignoredPaths)))
        else
          // nothing to prepare
          (expected, actual).asRight

      AssertStep(
        title = jsonAssertionTitleBuilder(titleBuilder, ignoredKeys, whitelist),
        action = sc => Assertion.either {
          if (whitelist && ignoredKeys.nonEmpty)
            InvalidIgnoringConfigError.asLeft
          else
            for {
              sessionValue <- sc.session.get(sessionKey)
              sessionValueWithFocusJson <- resolveRunMandatoryJsonPath(jsonPath, sessionValue, sc)
              tuple1 <- handleMatchers(sc, sessionValueWithFocusJson)(expected, expectedShow, negate)
              (expectedWithoutMatchers, actualWithoutMatchers, matcherAssertions) = tuple1
              tuple2 <- handleIgnoredFields(sc, expectedWithoutMatchers, actualWithoutMatchers)
              (expectedPrepared, actualPrepared) = tuple2
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
      val titleBuilder = new StringBuilder()
      titleBuilder.append(target)
      if (jsonPath != JsonPath.root) titleBuilder.append(s"'s field '$jsonPath'")
      titleBuilder.append(" is less than ")
      quoteInto(titleBuilder, lessThan.toString)
      AssertStep(
        title = jsonAssertionTitleBuilder(titleBuilder, ignoredKeys, whitelist),
        action = sc => Assertion.either {
          for {
            sessionValue <- sc.session.get(sessionKey)
            subJson <- resolveRunMandatoryJsonPath(jsonPath, sessionValue, sc)
            subJsonTyped <- decodeAs[A](subJson)
            resolvedExpected <- sc.fillPlaceholders(lessThan)
          } yield LessThanAssertion(subJsonTyped, resolvedExpected)

        }
      )
    }

    def isGreaterThan[A: Show: Order: Resolvable: Decoder](greaterThan: A): AssertStep = {
      val titleBuilder = new StringBuilder()
      titleBuilder.append(target)
      if (jsonPath != JsonPath.root) titleBuilder.append(s"'s field '$jsonPath'")
      titleBuilder.append(" is greater than ")
      quoteInto(titleBuilder, greaterThan.toString)
      AssertStep(
        title = jsonAssertionTitleBuilder(titleBuilder, ignoredKeys, whitelist),
        action = sc => Assertion.either {
          for {
            sessionValue <- sc.session.get(sessionKey)
            subJson <- resolveRunMandatoryJsonPath(jsonPath, sessionValue, sc)
            subJsonTyped <- decodeAs[A](subJson)
            resolvedExpected <- sc.fillPlaceholders(greaterThan)
          } yield GreaterThanAssertion(subJsonTyped, resolvedExpected)
        }
      )
    }

    def isBetween[A: Show: Order: Resolvable: Decoder](less: A, greater: A): AssertStep = {
      val titleBuilder = new StringBuilder()
      titleBuilder.append(target)
      if (jsonPath != JsonPath.root) titleBuilder.append(s"'s field '$jsonPath'")
      titleBuilder.append(s" is between '$less' and '$greater'")
      AssertStep(
        title = jsonAssertionTitleBuilder(titleBuilder, ignoredKeys, whitelist),
        action = sc => Assertion.either {
          for {
            sessionValue <- sc.session.get(sessionKey)
            subJson <- resolveRunMandatoryJsonPath(jsonPath, sessionValue, sc)
            subJsonTyped <- decodeAs[A](subJson)
            resolvedLess <- sc.fillPlaceholders(less)
            resolvedGreater <- sc.fillPlaceholders(greater)
          } yield BetweenAssertion(resolvedLess, subJsonTyped, resolvedGreater)
        }
      )
    }

    def containsString(expectedPart: String): AssertStep = {
      val titleBuilder = new StringBuilder()
      titleBuilder.append(target)
      if (jsonPath != JsonPath.root) titleBuilder.append(s"'s field '$jsonPath'")
      titleBuilder.append(" contains ")
      quoteInto(titleBuilder, expectedPart)
      AssertStep(
        title = jsonAssertionTitleBuilder(titleBuilder, ignoredKeys, whitelist),
        action = sc => Assertion.either {
          for {
            sessionValue <- sc.session.get(sessionKey)
            subJson <- resolveRunMandatoryJsonPath(jsonPath, sessionValue, sc)
            resolvedExpected <- sc.fillPlaceholders(expectedPart)
          } yield StringContainsAssertion(subJson.show, resolvedExpected)
        }
      )
    }

    def matchesRegex(expectedRegex: Regex): AssertStep = {
      val titleBuilder = new StringBuilder()
      titleBuilder.append(target)
      if (jsonPath != JsonPath.root) titleBuilder.append(s"'s field '$jsonPath'")
      titleBuilder.append(" matches ")
      quoteInto(titleBuilder, expectedRegex.toString)
      AssertStep(
        title = jsonAssertionTitleBuilder(titleBuilder, ignoredKeys, whitelist),
        action = sc => Assertion.either {
          for {
            sessionValue <- sc.session.get(sessionKey)
            subJson <- resolveRunMandatoryJsonPath(jsonPath, sessionValue, sc)
          } yield RegexAssertion(subJson.show, expectedRegex)
        }
      )
    }

    def isNull: AssertStep = {
      val titleBuilder = new StringBuilder()
      titleBuilder.append(target)
      if (jsonPath != JsonPath.root) titleBuilder.append(s"'s field '$jsonPath'")
      titleBuilder.append(" is null")
      AssertStep(
        title = jsonAssertionTitleBuilder(titleBuilder, ignoredKeys, whitelist),
        action = sc => Assertion.either {
          for {
            sessionValue <- sc.session.get(sessionKey)
            subJson <- resolveRunMandatoryJsonPath(jsonPath, sessionValue, sc)
          } yield GenericEqualityAssertion(Json.Null, subJson)
        }
      )
    }

    def isNotNull: AssertStep = {
      val titleBuilder = new StringBuilder()
      titleBuilder.append(target)
      if (jsonPath != JsonPath.root) titleBuilder.append(s"'s field '$jsonPath'")
      titleBuilder.append(" is not null")
      AssertStep(
        title = jsonAssertionTitleBuilder(titleBuilder, ignoredKeys, whitelist),
        action = sc => Assertion.either {
          for {
            sessionValue <- sc.session.get(sessionKey)
            subJson <- resolveRunMandatoryJsonPath(jsonPath, sessionValue, sc)
          } yield CustomMessageEqualityAssertion(false, subJson.isNull, () => keyIsNotNullError(jsonPath, sessionValue))
        }
      )
    }

    def isAbsent: AssertStep = {
      val titleBuilder = new StringBuilder()
      titleBuilder.append(target)
      if (jsonPath != JsonPath.root) titleBuilder.append(s"'s field '$jsonPath'")
      titleBuilder.append(" is absent")
      AssertStep(
        title = jsonAssertionTitleBuilder(titleBuilder, ignoredKeys, whitelist),
        action = sc => Assertion.either {
          for {
            sessionValue <- sc.session.get(sessionKey)
            subJson <- resolveRunJsonPath(jsonPath, sessionValue, sc)
          } yield CustomMessageEqualityAssertion(true, subJson.isEmpty, () => keyIsPresentError(jsonPath, subJson.get)) //YOLO
        }
      )
    }

    def isPresent: AssertStep = {
      val titleBuilder = new StringBuilder()
      titleBuilder.append(target)
      if (jsonPath != JsonPath.root) titleBuilder.append(s"'s field '$jsonPath'")
      titleBuilder.append(" is present")
      AssertStep(
        title = jsonAssertionTitleBuilder(titleBuilder, ignoredKeys, whitelist),
        action = sc => Assertion.either {
          for {
            sessionValue <- sc.session.get(sessionKey)
            subJson <- resolveRunJsonPath(jsonPath, sessionValue, sc)
          } yield CustomMessageEqualityAssertion(true, subJson.isDefined, () => keyIsAbsentError(jsonPath, sessionValue))
        }
      )
    }

    // (previousValue, currentValue) => Assertion
    def compareWithPreviousValue[A: Decoder](comp: (A, A) => Assertion): AssertStep = {
      val titleBuilder = new StringBuilder()
      titleBuilder.append("compare previous & current value of ")
      titleBuilder.append(target)
      if (jsonPath != JsonPath.root) titleBuilder.append(s"'s field '$jsonPath'")
      AssertStep(
        title = jsonAssertionTitleBuilder(titleBuilder, ignoredKeys, whitelist),
        action = sc => Assertion.either {
          for {
            ignoredPaths <- traverse(ignoredKeys)(resolveAndParseJsonPath(_, sc))
            jsonPathFocus <- resolveAndParseJsonPath(jsonPath, sc)
            current <- sc.session.get(sessionKey)
            previous <- sc.session.getMandatoryPrevious(sessionKey.name)
            subCurrentTyped <- focusIgnoreDecode(current, jsonPathFocus, ignoredPaths)
            subPreviousTyped <- focusIgnoreDecode(previous, jsonPathFocus, ignoredPaths)
          } yield comp(subPreviousTyped, subCurrentTyped)
        }
      )
    }

    private def focusIgnoreDecode[A: Decoder](v: String, jsonPath: JsonPath, ignoredPaths: Seq[JsonPath]): Either[CornichonError, A] =
      for {
        subJson <- jsonPath.runStrict(v)
        subIgnoredJson = removeFieldsByPath(subJson, ignoredPaths)
        subCurrentTyped <- decodeAs[A](subIgnoredJson)
      } yield subCurrentTyped

    // unordered by default
    def asArray: JsonArrayStepBuilder =
      if (ignoredKeys.nonEmpty)
        throw UseIgnoringEach.toException
      else
        JsonArrayStepBuilder(sessionKey, jsonPath, ordered = false, ignoredEachKeys = Nil, prettySessionKeyTitle)
  }

  case class JsonArrayStepBuilder(
      private val sessionKey: SessionKey,
      private val jsonPath: String,
      private val ordered: Boolean,
      private val ignoredEachKeys: List[String],
      private val prettySessionKeyTitle: Option[String] = None
  ) {

    private val target = prettySessionKeyTitle.getOrElse(sessionKey)

    def inOrder: JsonArrayStepBuilder = copy(ordered = true)

    def ignoringEach(ignoringEach: String*): JsonArrayStepBuilder = copy(ignoredEachKeys = ignoringEach.toList)

    def isNotEmpty: AssertStep = AssertStep(
      title = if (jsonPath == JsonPath.root) s"$target array size is not empty" else s"$target's array '$jsonPath' size is not empty",
      action = sc => Assertion.either {
        for {
          sessionValue <- sc.session.get(sessionKey)
          elements <- applyPathAndFindArray(jsonPath)(sc, sessionValue)
        } yield CustomMessageEqualityAssertion(true, elements.nonEmpty, () => jsonArrayNotEmptyError(parseDslJsonUnsafe(sessionValue).show))
      }
    )

    def isEmpty: AssertStep = hasSize(0)

    def size: GenericAssertStepBuilder[Int] = new GenericAssertStepBuilder[Int] {
      override val baseTitle: String = if (jsonPath == JsonPath.root) s"$target array size" else s"$target's array '$jsonPath' size"

      override def sessionExtractor(sc: ScenarioContext): Either[CornichonError, (Int, Some[() => String])] =
        for {
          sessionValue <- sc.session.get(sessionKey)
          elements <- applyPathAndFindArray(jsonPath)(sc, sessionValue)
        } yield (elements.size, Some(() => Json.fromValues(elements).show))
    }

    def hasSize(expectedSize: Int): AssertStep = AssertStep(
      title = if (jsonPath == JsonPath.root) s"$target array size is '$expectedSize'" else s"$target's array '$jsonPath' size is '$expectedSize'",
      action = sc => Assertion.either {
        for {
          sessionValue <- sc.session.get(sessionKey)
          elements <- applyPathAndFindArray(jsonPath)(sc, sessionValue)
        } yield CustomMessageEqualityAssertion(expectedSize, elements.size, () => arraySizeError(expectedSize, elements))
      }
    )

    def is[A: Show: Resolvable: Encoder](expected: Either[CornichonError, A]): AssertStep = expected match {
      case Left(e) =>
        val titleBuilder = new StringBuilder()
        titleBuilder.append(target)
        if (jsonPath == JsonPath.root)
          titleBuilder.append(" array ")
        else {
          titleBuilder.append("'s array ")
          quoteInto(titleBuilder, jsonPath)
          titleBuilder.append(" ")
        }
        AssertStep(jsonAssertionTitleBuilder(titleBuilder, ignoredEachKeys), _ => Assertion.either(e.asLeft))
      case Right(a) =>
        is(a)
    }

    def is[A: Show: Resolvable: Encoder](expected: A): AssertStep = {
      val expectedShow = expected.show

      val titleBuilder = new StringBuilder()
      // target
      titleBuilder.append(target)

      // target path
      if (jsonPath == JsonPath.root)
        titleBuilder.append(" array ")
      else {
        titleBuilder.append("'s array ")
        quoteInto(titleBuilder, jsonPath)
        titleBuilder.append(" ")
      }

      // ordered
      if (ordered) titleBuilder.append("in order ")

      // expected
      titleBuilder.append("is\n")
      titleBuilder.append(expectedShow)

      AssertStep(
        title = jsonAssertionTitleBuilder(titleBuilder, ignoredEachKeys),
        action = sc => Assertion.either {
          for {
            matchers <- sc.findAllMatchers(expectedShow)
            _ <- if (matchers.nonEmpty) MatchersNotSupportedInAsArray(matchers).asLeft else Done.rightDone
            expectedArrayJson <- resolveAndParseJson(expected, sc)
            expectedArray <- Either.fromOption(expectedArrayJson.asArray, NotAnArrayError(expected))
            expectedArrayWithIgnore <- removeIgnoredPathFromElements(sc, expectedArray)
            sessionValue <- sc.session.get(sessionKey)
            arrayFromSession <- applyPathAndFindArray(jsonPath)(sc, sessionValue)
            actualValueWithIgnore <- removeIgnoredPathFromElements(sc, arrayFromSession)
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
      val title = jsonArrayContainsTitleBuilder(exactly = false, negate = true, target.toString, jsonPath, elements: _*)
      bodyContainsElements(title, elements, expected = false)
    }

    def contains[A: Show: Resolvable: Encoder](elements: A*): AssertStep = {
      val title = jsonArrayContainsTitleBuilder(exactly = false, negate = false, target.toString, jsonPath, elements: _*)
      bodyContainsElements(title, elements, expected = true)
    }

    private def bodyContainsElements[A: Show: Resolvable: Encoder](title: String, expectedElements: Seq[A], expected: Boolean) =
      AssertStep(
        title = title,
        action = sc => Assertion.either {
          for {
            sessionValue <- sc.session.get(sessionKey)
            jArr <- applyPathAndFindArray(jsonPath)(sc, sessionValue)
            actualValue <- removeIgnoredPathFromElements(sc, jArr)
            resolvedJson <- traverseIV(expectedElements.iterator)(resolveAndParseJson(_, sc))
            containsAll = resolvedJson.forall(actualValue.contains)
          } yield CustomMessageEqualityAssertion(expected, containsAll, () => arrayContainsError(resolvedJson, jArr, expected))
        }
      )

    def containsExactly[A: Show: Resolvable: Encoder](elements: A*): AssertStep = {
      val title = jsonArrayContainsTitleBuilder(exactly = true, negate = false, target.toString, jsonPath, elements: _*)
      bodyContainsExactlyElements(title, elements)
    }

    private def bodyContainsExactlyElements[A: Show: Resolvable: Encoder](title: String, expectedElements: Seq[A]) =
      AssertStep(
        title = title,
        action = sc => Assertion.either {
          for {
            sessionValue <- sc.session.get(sessionKey)
            jArr <- applyPathAndFindArray(jsonPath)(sc, sessionValue)
            actualValue <- removeIgnoredPathFromElements(sc, jArr)
            expectedResolvedJson <- traverseIV(expectedElements.iterator)(resolveAndParseJson(_, sc))
          } yield CollectionsContainSameElements(expectedResolvedJson, actualValue)
        }
      )

    private def removeIgnoredPathFromElements(scenarioContext: ScenarioContext, jArray: Vector[Json]): Either[CornichonError, Vector[Json]] = {
      if (ignoredEachKeys.isEmpty)
        Right(jArray)
      else
        traverse(ignoredEachKeys)(resolveAndParseJsonPath(_, scenarioContext))
          .map(ignoredPaths => jArray.map(removeFieldsByPath(_, ignoredPaths)))
    }

    private def applyPathAndFindArray(path: String)(scenarioContext: ScenarioContext, sessionValue: String): Either[CornichonError, Vector[Json]] =
      if (path == JsonPath.root)
        parseArray(sessionValue)
      else
        resolveAndParseJsonPath(path, scenarioContext).flatMap(selectMandatoryArrayJsonPath(sessionValue, _))
  }

  // How to use this for array Vector[Json]?
  private def handleMatchers[A: Resolvable: Show: Encoder](sc: ScenarioContext, sessionValueWithFocusJson: Json)(expected: A, expectedShow: String, negate: Boolean): Either[CornichonError, (Json, Json, List[MatcherAssertion])] =
    sc.findAllMatchers(expectedShow).flatMap { matchers =>
      if (matchers.nonEmpty) {
        val withQuotedMatchers = Resolvable[A].transformResolvableForm(expected) { r =>
          // don't add quotes if is not a complex JsonObject otherwise it would produce a double-quoted string
          if (isJsonString(r)) r
          else MatcherResolver.quoteMatchers(r, matchers)
        }
        resolveAndParseJson(withQuotedMatchers, sc).map {
          expectedJson => MatcherResolver.prepareMatchers(matchers, expectedJson, sessionValueWithFocusJson, negate)
        }
      } else
        resolveAndParseJson(expected, sc).map {
          expectedJson => (expectedJson, sessionValueWithFocusJson, Nil)
        }
    }

  private def jsonArrayContainsTitleBuilder[A: Show](exactly: Boolean, negate: Boolean, target: String, jsonPath: String, elements: A*): String = {
    val builder = new StringBuilder()
    // target
    builder.append(target)

    // path
    if (jsonPath == JsonPath.root) {
      builder.append(" array ")
    } else {
      builder.append("'s array ")
      quoteInto(builder, jsonPath)
      builder.append(" ")
    }

    // negate
    if (negate) {
      builder.append("does not contain")
    } else {
      builder.append("contains")
    }

    // exactly
    if (exactly) builder.append(" exactly")
    builder.append("\n")

    // elements
    val count = elements.length
    var i = 0
    elements.foreach { e =>
      builder.append(e.show)
      if (i < count - 1) builder.append(" and ")
      i += 1
    }

    builder.result()
  }

  private def jsonAssertionTitleBuilder(titleBuilder: StringBuilder, ignoring: Seq[String], withWhiteListing: Boolean = false): String = {
    // whitelisting
    if (withWhiteListing)
      titleBuilder.append(" with white listing")

    // ignored keys
    if (ignoring.nonEmpty) {
      titleBuilder.append(" ignoring keys ")
      val len = ignoring.length
      var i = 0
      ignoring.foreach { key =>
        titleBuilder.append(key)
        if (i < len - 1) titleBuilder.append(", ")
        i += 1
      }
    }

    titleBuilder.toString()
  }

  private def resolveAndParseJson[A: Show: Encoder: Resolvable](input: A, sc: ScenarioContext): Either[CornichonError, Json] =
    sc.fillPlaceholders(input).flatMap(r => parseDslJson(r))

  private def resolveAndParseJsonPath(path: String, sc: ScenarioContext): Either[CornichonError, JsonPath] =
    sc.fillPlaceholders(path).flatMap(JsonPath.parse)

  private def resolveRunJsonPath(path: String, source: String, sc: ScenarioContext): Either[CornichonError, Option[Json]] =
    resolveAndParseJsonPath(path, sc).flatMap(_.run(source))

  private def resolveRunMandatoryJsonPath(path: String, source: String, sc: ScenarioContext): Either[CornichonError, Json] =
    resolveAndParseJsonPath(path, sc).flatMap(_.runStrict(source))
}
