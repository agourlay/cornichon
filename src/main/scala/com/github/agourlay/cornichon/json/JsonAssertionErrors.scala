package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.core.CornichonError
import io.circe.Json
import cats.syntax.show._

object JsonAssertionErrors {

  def keyIsPresentError(keyName: String, source: String): Boolean ⇒ String = resFalse ⇒ {
    s"""expected key '$keyName' to be absent but it was found with value :
        |$source""".stripMargin
  }

  def keyIsAbsentError(keyName: String, source: String): Boolean ⇒ String = resFalse ⇒ {
    s"""expected key '$keyName' to be present but it was not in the source :
        |$source""".stripMargin
  }

  case object InvalidIgnoringConfigError extends CornichonError {
    val msg = "usage of 'ignoring' and 'whiteListing' is mutually exclusive"
  }

  case object UseIgnoringEach extends CornichonError {
    val msg = "use 'ignoringEach' when asserting on an array"
  }

  def arraySizeError(expected: Int, sourceArray: String): Int ⇒ String = actual ⇒ {
    val base = s"""expected array size '$expected' but actual size is '$actual'"""
    if (actual != 0)
      base + s""" with array:
                 |$sourceArray""".stripMargin
    else base
  }

  def jsonArrayNotEmptyError(jsonArray: Json): Boolean ⇒ String = actual ⇒ {
    s"""expected JSON array to be empty but it is not the case with array:
       |${jsonArray.show}""".stripMargin
  }

  def arrayContainsError(expected: Seq[String], sourceArray: String, contains: Boolean): Boolean ⇒ String = resFalse ⇒ {
    s"""expected array to ${if (contains) "" else "not "}contain
        |'${expected.mkString(" and ")}'
        |but it is not the case with array:
        |$sourceArray""".stripMargin
  }

}
