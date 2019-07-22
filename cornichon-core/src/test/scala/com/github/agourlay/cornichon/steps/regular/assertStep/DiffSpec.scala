package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.instances.string._
import org.scalatest.{ Matchers, WordSpec }

class DiffSpec extends WordSpec with Matchers {

  "Diff" when {

    "orderedCollectionDiff" must {
      "detect missing elements" in {
        Diff.orderedCollectionDiff(Seq("a", "b"), Seq.empty) should be(
          """|Ordered collection diff. between actual result and expected result is :
             |
             |deleted elements:
             |a
             |b""".stripMargin
        )
      }

      "detect added elements" in {
        Diff.orderedCollectionDiff(Seq.empty, Seq("a", "b")) should be(
          """|Ordered collection diff. between actual result and expected result is :
             |added elements:
             |a
             |b""".stripMargin
        )
      }

      "detect moved added elements" in {
        Diff.orderedCollectionDiff(Seq("a", "b"), Seq("b", "a")) should be(
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

      "detect mixed case" in {
        Diff.orderedCollectionDiff(Seq("a", "b", "c", "e"), Seq("b", "a", "d", "f")) should be(
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
    }

    "notOrderedCollectionDiff" must {
      "detect missing elements" in {
        Diff.notOrderedCollectionDiff(Set("a", "b"), Set.empty) should be(
          """|Not ordered collection diff. between actual result and expected result is :
             |
             |deleted elements:
             |a
             |b""".stripMargin
        )
      }

      "detect added elements" in {
        Diff.notOrderedCollectionDiff(Set.empty, Set("a", "b")) should be(
          """|Not ordered collection diff. between actual result and expected result is :
             |added elements:
             |a
             |b""".stripMargin
        )
      }

      "not detect moved added elements" in {
        Diff.notOrderedCollectionDiff(Set("a", "b"), Set("b", "a")) should be(
          "Not ordered collection diff. between actual result and expected result is :"
        )
      }

      "detect mixed case" in {
        Diff.notOrderedCollectionDiff(Set("a", "b", "c", "e"), Set("b", "a", "d", "f")) should be(
          """|Not ordered collection diff. between actual result and expected result is :
             |added elements:
             |d
             |f
             |deleted elements:
             |c
             |e""".stripMargin
        )
      }
    }
  }
}
