package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.util.ShowInstances._

import cats.syntax.show._

object HttpAssertionErrors {

  // TODO do not assume that body is JSON - use content-type
  def statusError(expected: Int, body: String): Int ⇒ String = actual ⇒ {
    s"""expected '$expected' but actual is '$actual' with response body:
      |${parseJsonUnsafe(body).show}""".stripMargin
  }

  def headersDoesNotContainError(expected: String, sourceArray: String): Boolean ⇒ String = resFalse ⇒ {
    val prettyHeaders = displayStringPairs(decodeSessionHeaders(sourceArray))
    s"""expected headers to contain $expected but it is not the case with headers:
      |$prettyHeaders""".stripMargin
  }
}
