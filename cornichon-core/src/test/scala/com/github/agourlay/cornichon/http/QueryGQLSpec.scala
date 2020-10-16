package com.github.agourlay.cornichon.http

import io.circe.Json
import utest._

object QueryGQLSpec extends TestSuite {

  val tests = Tests {
    test("QueryGQL allow adding variables of different types") {
      val gql = QueryGQL("url", QueryGQL.emptyDocument, None, None, Nil, Nil)
      val withVariables = gql.withVariables(
        "booleanVar" -> true,
        "intVar" -> 42,
        "stringVar" -> "hello",
        "arrayOfString" -> """ ["value1", "value2"] """
      )
      assert(withVariables.variables.get == Map(
        "booleanVar" -> Json.True,
        "intVar" -> Json.fromInt(42),
        "stringVar" -> Json.fromString("hello"),
        "arrayOfString" -> Json.fromValues(Json.fromString("value1") :: Json.fromString("value2") :: Nil)
      ))
    }
  }
}
