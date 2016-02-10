package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson._

object HttpDslErrors {

  def statusError(expected: Int, body: String): Int ⇒ String = actual ⇒ {
    s"""expected '$expected' but actual is '$actual' with response body:
        |${prettyPrint(parseJson(body))}""".stripMargin
  }

  def arraySizeError(expected: Int, sourceArray: String): Int ⇒ String = actual ⇒ {
    s"""expected array size '$expected' but actual is '$actual' with array:
        |$sourceArray""".stripMargin
  }

  def arrayDoesNotContainError(expected: String, sourceArray: String): Boolean ⇒ String = resFalse ⇒ {
    s"""expected array to contain '$expected' but it is not the case with array:
        |$sourceArray""".stripMargin
  }

  def headersDoesNotContainError(expected: String, sourceArray: String): Boolean ⇒ String = resFalse ⇒ {
    s"""expected headers to contain '$expected' but it is not the case with headers:
        |$sourceArray""".stripMargin
  }

  case object InvalidIgnoringConfigError extends CornichonError {
    val msg = "usage of 'ignoring' and 'whiteListing' is mutually exclusive"
  }

}
