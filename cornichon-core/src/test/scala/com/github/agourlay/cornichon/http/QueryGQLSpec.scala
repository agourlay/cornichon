package com.github.agourlay.cornichon.http

import io.circe.Json
import org.scalatest.{ Matchers, OptionValues, WordSpec }

class QueryGQLSpec extends WordSpec
  with Matchers
  with OptionValues {

  "QueryGQL" must {
    "allow adding variables of different types" in {
      val gql = QueryGQL("url", QueryGQL.emptyDocument, None, None, Nil, Nil)
      val withVariables = gql.withVariables(
        "booleanVar" -> true,
        "intVar" -> 42,
        "stringVar" -> "hello"
      )
      withVariables.variables.value should be(Map(
        "booleanVar" -> Json.True,
        "intVar" -> Json.fromInt(42),
        "stringVar" -> Json.fromString("hello")
      ))
    }
  }

}
