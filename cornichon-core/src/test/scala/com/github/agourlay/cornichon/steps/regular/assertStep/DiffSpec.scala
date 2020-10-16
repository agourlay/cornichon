package com.github.agourlay.cornichon.steps.regular.assertStep

import com.github.agourlay.cornichon.steps.regular.assertStep.Diff._
import io.circe.Json
import utest._

object DiffSpec extends TestSuite {

  val tests = Tests {
    test("orderedCollectionDiff detect missing elements") {
      assert(orderedCollectionDiff(Seq("a", "b"), Nil) ==
        """|Ordered collection diff. between actual result and expected result is :
           |
           |deleted elements:
           |a
           |b""".stripMargin)
    }

    test("orderedCollectionDiff detect added elements") {
      assert(orderedCollectionDiff(Nil, Seq("a", "b")) ==
        """|Ordered collection diff. between actual result and expected result is :
           |added elements:
           |a
           |b""".stripMargin)
    }

    test("orderedCollectionDiff detect moved added elements") {
      assert(orderedCollectionDiff(Seq("a", "b"), Seq("b", "a")) ==
        """|Ordered collection diff. between actual result and expected result is :
           |
           |
           |moved elements:
           |from index 0 to index 1
           |a
           |from index 1 to index 0
           |b""".stripMargin
      )
    }

    test("orderedCollectionDiff detect mixed case") {
      assert(orderedCollectionDiff(Seq("a", "b", "c", "e"), Seq("b", "a", "d", "f")) ==
        """|Ordered collection diff. between actual result and expected result is :
           |added elements:
           |d
           |f
           |deleted elements:
           |c
           |e
           |moved elements:
           |from index 0 to index 1
           |a
           |from index 1 to index 0
           |b""".stripMargin
      )
    }

    test("notOrderedCollectionDiff detect missing elements") {
      assert(notOrderedCollectionDiff(Set("a", "b"), Set.empty) ==
        """|Not ordered collection diff. between actual result and expected result is :
           |
           |deleted elements:
           |a
           |b""".stripMargin
      )
    }

    test("notOrderedCollectionDiff detect added elements") {
      assert(notOrderedCollectionDiff(Set.empty, Set("a", "b")) ==
        """|Not ordered collection diff. between actual result and expected result is :
           |added elements:
           |a
           |b""".stripMargin
      )
    }

    test("notOrderedCollectionDiff not detect moved added elements") {
      assert(notOrderedCollectionDiff(Set("a", "b"), Set("b", "a")) ==
        "Not ordered collection diff. between actual result and expected result is :"
      )
    }

    test("notOrderedCollectionDiff detect mixed case") {
      assert(notOrderedCollectionDiff(Set("a", "b", "c", "e"), Set("b", "a", "d", "f")) ==
        """|Not ordered collection diff. between actual result and expected result is :
           |added elements:
           |d
           |f
           |deleted elements:
           |c
           |e""".stripMargin
      )
    }

    test("JSON diff for JString is done via JsonPatch") {
      val diff = jsonDiff.diff(Json.fromString("test"), Json.fromString("test1"))
      val expected =
        """|JSON patch between actual result and expected result is :
           |[
           |  {
           |    "op" : "replace",
           |    "path" : "",
           |    "value" : "test1",
           |    "old" : "test"
           |  }
           |]""".stripMargin
      assert(diff.contains(expected))
    }

    test("JSON diff for JObject is done via JsonPatch") {
      val diff = jsonDiff.diff(Json.fromFields(("test" -> Json.fromString("value")) :: Nil), Json.fromFields(("test" -> Json.fromString("new value")) :: Nil))
      val expected =
        """|JSON patch between actual result and expected result is :
           |[
           |  {
           |    "op" : "replace",
           |    "path" : "/test",
           |    "value" : "new value",
           |    "old" : "value"
           |  }
           |]""".stripMargin
      assert(diff.contains(expected))
    }

    test("JSON diff for JArray is done via JsonPatch") {
      val diff = jsonDiff.diff(Json.fromValues(Json.fromFields(("test" -> Json.fromString("value")) :: Nil) :: Nil), Json.fromValues(Json.fromFields(("test" -> Json.fromString("new value")) :: Nil) :: Nil))
      val expected =
        """|JSON patch between actual result and expected result is :
           |[
           |  {
           |    "op" : "replace",
           |    "path" : "/0/test",
           |    "value" : "new value",
           |    "old" : "value"
           |  }
           |]""".stripMargin
      assert(diff.contains(expected))
    }

  }
}
