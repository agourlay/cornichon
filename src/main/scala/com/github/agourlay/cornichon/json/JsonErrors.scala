package com.github.agourlay.cornichon.json

import cats.Show
import cats.syntax.show._
import com.github.agourlay.cornichon.core.CornichonError
import io.circe.Json

sealed trait JsonError extends CornichonError

case class NotAnArrayError[A: Show](badPayload: A) extends JsonError {
  val baseErrorMessage = s"""expected JSON Array but got
               |${badPayload.show}""".stripMargin
}

case class MalformedJsonError[A: Show](input: A, message: String) extends JsonError {
  val baseErrorMessage =
    s"""malformed JSON error
       |$message
       |for input
       |${input.show}""".stripMargin
}

case class MalformedGraphQLJsonError[A](input: A, exception: Throwable) extends JsonError {
  val baseErrorMessage = s"malformed GraphQLJSON input $input with ${exception.getMessage}"
}

case class JsonPathParsingError(input: String, error: String) extends JsonError {
  val baseErrorMessage = s"error thrown during JsonPath parsing for input '$input' : $error"
}

case class JsonPathError(input: String, error: Throwable) extends JsonError {
  val baseErrorMessage = s"error thrown during JsonPath parsing for input '$input' : ${error.getMessage}"
}

case class WhitelistingError(elementNotDefined: String, source: String) extends JsonError {
  val baseErrorMessage =
    s"""whitelisting error
       |$elementNotDefined
       |is not defined in object
       |$source""".stripMargin
}

case class NotStringFieldError(input: Json, field: String) extends JsonError {
  val baseErrorMessage =
    s"""field '$field' is not of type String in JSON
       |${input.show}""".stripMargin
}
