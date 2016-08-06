package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.util.Formats._

object HttpDslErrors {

  def statusError(expected: Int, body: String): Int ⇒ String = actual ⇒ {
    s"""expected '$expected' but actual is '$actual' with response body:
      |${prettyPrint(parseJsonUnsafe(body))}""".stripMargin
  }

  def arraySizeError(expected: Int, sourceArray: String): Int ⇒ String = actual ⇒ {
    val base = s"""expected array size '$expected' but actual size is '$actual'"""
    if (actual != 0)
      base + """ with array:
      |$sourceArray""".stripMargin
    else base
  }

  def arrayContainsError(expected: Seq[String], sourceArray: String, contains: Boolean): Boolean ⇒ String = resFalse ⇒ {
    s"""expected array to ${if (contains) "" else "not "}contain
      |'${expected.mkString(" and ")}'
      |but it is not the case with array:
      |$sourceArray""".stripMargin
  }

  def headersDoesNotContainError(expected: String, sourceArray: String): Boolean ⇒ String = resFalse ⇒ {
    val prettyHeaders = displayTuples(decodeSessionHeaders(sourceArray))
    s"""expected headers to contain $expected but it is not the case with headers:
      |$prettyHeaders""".stripMargin
  }

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
    val msg = "use 'ignoringEach' when asserting on a body as an array"
  }
}
