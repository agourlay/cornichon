package com.github.agourlay.cornichon.json

import cats.Show
import cats.syntax.show._
import com.github.agourlay.cornichon.core.CornichonError
import io.circe.Json

sealed trait JsonError extends CornichonError

case class NotAnArrayError[A: Show](badPayload: A) extends JsonError {
  lazy val baseErrorMessage =
    s"""expected JSON Array but got
       |${badPayload.show}""".stripMargin
}

case class MalformedJsonError[A: Show](input: A, message: String) extends JsonError {
  lazy val baseErrorMessage =
    s"""malformed JSON error
       |$message
       |for input
       |${input.show}""".stripMargin
}

case class MalformedGraphQLJsonError[A](input: A, exception: Throwable) extends JsonError {
  lazy val baseErrorMessage = s"malformed GraphQLJSON input $input with ${exception.getMessage}"
}

case class JsonPathParsingError(input: String, error: String) extends JsonError {
  lazy val baseErrorMessage = s"error thrown during JsonPath parsing for input '$input' : $error"
}

case class JsonPathError(input: String, error: Throwable) extends JsonError {
  lazy val baseErrorMessage = s"error thrown during JsonPath parsing for input '$input' : ${error.getMessage}"
}

case class WhitelistingError(missingFields: Iterable[String], source: Json) extends JsonError {
  lazy val baseErrorMessage =
    s"""whitelisting error because the following field(s)
       |${missingFields.mkString("\n")}
       |can not be found in JSON object
       |${source.show}""".stripMargin
}

case class NotStringFieldError(input: Json, field: String) extends JsonError {
  lazy val baseErrorMessage =
    s"""field '$field' is not of type String in JSON
       |${input.show}""".stripMargin
}

case class PathSelectsNothing(path: String, input: Json) extends JsonError {
  lazy val baseErrorMessage =
    s"""JSON path '$path' is not defined in object
       |${input.show}""".stripMargin
}