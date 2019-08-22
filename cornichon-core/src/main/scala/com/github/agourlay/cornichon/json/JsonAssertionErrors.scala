package com.github.agourlay.cornichon.json

import io.circe.Json
import cats.syntax.show._
import cats.instances.string._
import com.github.agourlay.cornichon.json.CornichonJson.parseDslJsonUnsafe
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.matchers.Matcher

object JsonAssertionErrors {

  def keyIsNotNullError(keyName: String, source: String): String = {
    val prettySource = parseDslJsonUnsafe(source).show
    s"""expected key '$keyName' to not be null but it was null in the source:
       |$prettySource""".stripMargin
  }

  def keyIsPresentError(keyName: String, source: Json): String = {
    s"""expected key '$keyName' to be absent but it was found with value:
        |${source.show}""".stripMargin
  }

  def keyIsAbsentError(keyName: String, source: String): String = {
    val prettySource = parseDslJsonUnsafe(source).show
    s"""expected key '$keyName' to be present but it was not in the source:
        |$prettySource""".stripMargin
  }

  object InvalidIgnoringConfigError extends CornichonError {
    val baseErrorMessage = "usage of 'ignoring' and 'whiteListing' is mutually exclusive"
  }

  object UseIgnoringEach extends CornichonError {
    val baseErrorMessage = "use 'ignoringEach' when asserting on an array"
  }

  def arraySizeError(expected: Int, arrayElements: Vector[Json]): String = {
    val sourceArray = Json.fromValues(arrayElements).show
    val actualSize = arrayElements.size
    val base = s"expected array size '$expected' but actual size is '$actualSize'"
    if (actualSize != 0)
      base + s" with array:\n$sourceArray"
    else base
  }

  def jsonArrayNotEmptyError(context: String): String = {
    s"""expected JSON array to not be empty but it is not the case in the context of:
       |$context""".stripMargin
  }

  def arrayContainsError(expected: Vector[Json], sourceArray: Vector[Json], contains: Boolean): String = {
    val prettyExpected = expected.map(_.show)
    val prettySource = Json.fromValues(sourceArray).show
    s"""expected array to ${if (contains) "" else "not "}contain
        |'${prettyExpected.mkString(" and ")}'
        |but it is not the case with array:
        |$prettySource""".stripMargin
  }

  case class MatchersNotSupportedInAsArray(matchers: List[Matcher]) extends CornichonError {
    lazy val baseErrorMessage: String = s"matchers are not supported in `asArray` assertion but ${matchers.map(_.fullKey).mkString(", ")} found" +
      s"\nhttps://github.com/agourlay/cornichon/issues/135"
  }
}
