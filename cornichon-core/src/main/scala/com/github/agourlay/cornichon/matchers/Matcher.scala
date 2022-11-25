package com.github.agourlay.cornichon.matchers

import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.regex.Pattern

import io.circe.Json

import scala.util.Try

case class Matcher(key: String, description: String, predicate: Json => Boolean) {
  val fullKey = s"*$key*"
  val quotedFullKey = s""""$fullKey""""
  val pattern = Pattern.compile(Pattern.quote(fullKey))
}

object Matchers {

  val isPresent = Matcher(
    key = "is-present",
    description = "checks if the field is defined and not null",
    predicate = !_.isNull
  )

  val isNull = Matcher(
    key = "is-null",
    description = "checks if the field is defined and null",
    predicate = _.isNull
  )

  val anyString = Matcher(
    key = "any-string",
    description = "checks if the field is a String",
    predicate = _.isString
  )

  val anyArray = Matcher(
    key = "any-array",
    description = "checks if the field is an Array",
    predicate = _.isArray
  )

  val anyObject = Matcher(
    key = "any-object",
    description = "checks if the field is an Object",
    predicate = _.isObject
  )

  val anyInteger = Matcher(
    key = "any-integer",
    description = "checks if the field is an integer",
    predicate = _.isNumber
  )

  val anyPositiveInteger = Matcher(
    key = "any-positive-integer",
    description = "checks if the field is a positive integer",
    predicate = _.asNumber.flatMap(_.toInt).exists(_ > 0)
  )

  val anyNegativeInteger = Matcher(
    key = "any-negative-integer",
    description = "checks if the field is a negative integer",
    predicate = _.asNumber.flatMap(_.toInt).exists(_ < 0)
  )

  val anyUUID = Matcher(
    key = "any-uuid",
    description = "checks if the field is a valid UUID",
    predicate = _.asString.exists(s => Try(UUID.fromString(s)).isSuccess)
  )

  val anyBoolean = Matcher(
    key = "any-boolean",
    description = "checks if the field is a boolean",
    predicate = _.isBoolean
  )

  val anyAlphaNum = Matcher(
    key = "any-alphanum-string",
    description = "checks if the field is an alpha-numeric string",
    predicate = _.asString.exists(_.forall(_.isLetterOrDigit))
  )

  val anyDate = Matcher(
    key = "any-date",
    description = "checks if the field is an ISO date (e.g. 'yyyy-MM-dd')",
    predicate = _.asString.exists(s => Try(DateTimeFormatter.ISO_DATE.parse(s)).isSuccess)
  )

  val anyDateTime = Matcher(
    key = "any-date-time",
    description = """checks if the field is an ISO instant (e.g. "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")""",
    predicate = _.asString.exists(s => Try(DateTimeFormatter.ISO_INSTANT.parse(s)).isSuccess)
  )

  val anyTime = Matcher(
    key = "any-time",
    description = "checks if the field is an ISO time (e.g. 'HH:mm:ss.SSS')",
    predicate = _.asString.exists(s => Try(DateTimeFormatter.ISO_TIME.parse(s)).isSuccess)
  )
}