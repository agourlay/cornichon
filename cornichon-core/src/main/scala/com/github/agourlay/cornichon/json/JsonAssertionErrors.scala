package com.github.agourlay.cornichon.json

import io.circe.Json

import cats.syntax.show._
import cats.instances.string._

import com.github.agourlay.cornichon.json.CornichonJson.parseJsonUnsafe
import com.github.agourlay.cornichon.core.CornichonError

object JsonAssertionErrors {

  def keyIsPresentError(keyName: String, source: Json): String = {
    s"""expected key '$keyName' to be absent but it was found with value :
        |${source.show}""".stripMargin
  }

  def keyIsAbsentError(keyName: String, source: String): String = {
    val prettySource = parseJsonUnsafe(source).show
    s"""expected key '$keyName' to be present but it was not in the source :
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

  def jsonArrayNotEmptyError(arrayElements: Vector[Json]): String = {
    val jsonArray = Json.fromValues(arrayElements).show
    s"""expected JSON array to be empty but it is not the case with array:
       |$jsonArray""".stripMargin
  }

  def arrayContainsError(expected: Vector[Json], sourceArray: Vector[Json], contains: Boolean): String = {
    val prettyExpected = expected.map(_.show)
    val prettySource = Json.fromValues(sourceArray).show
    s"""expected array to ${if (contains) "" else "not "}contain
        |'${prettyExpected.mkString(" and ")}'
        |but it is not the case with array:
        |$prettySource""".stripMargin
  }

}
