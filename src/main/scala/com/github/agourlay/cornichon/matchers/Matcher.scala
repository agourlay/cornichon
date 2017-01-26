package com.github.agourlay.cornichon.matchers

import java.util.UUID

import scala.util.Try

case class Matcher(key: String, predicate: String ⇒ Boolean) {
  val fullKey = s"<<$key>>"
}

object Matchers {
  private val sdfDate = new java.text.SimpleDateFormat("yyyy-MM-dd")
  private val sdfDateTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
  private val sdfTime = new java.text.SimpleDateFormat("HH:mm:ss.SSS")

  val isPresent = Matcher("is-present", _.nonEmpty)
  val anyInteger = Matcher("any-integer", x ⇒ Try(Integer.parseInt(x)).isSuccess)
  val anyPositiveInteger = Matcher("any-positive-integer", x ⇒ Integer.parseInt(x) > 0)
  val anyNegativeIntger = Matcher("any-negative-integer", x ⇒ Integer.parseInt(x) < 0)
  val anyUUID = Matcher("any-uuid", x ⇒ Try(UUID.fromString(x)).isSuccess)
  val anyBoolean = Matcher("any-boolean", x ⇒ x == "true" || x == "false")
  val anyAlphaNum = Matcher("any-alphanum-string", _.forall(_.isLetterOrDigit))
  val anyDate = Matcher("any-date", x ⇒ Try(sdfDate.parse(x)).isSuccess)
  val anyDateTime = Matcher("any-date-time", x ⇒ Try(sdfDateTime.parse(x)).isSuccess)
  val anyTime = Matcher("any-time", x ⇒ Try(sdfTime.parse(x)).isSuccess)
}