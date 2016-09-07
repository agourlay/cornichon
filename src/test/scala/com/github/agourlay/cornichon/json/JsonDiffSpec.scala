package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.json.JsonDiff.Diff
import io.circe.Json
import org.scalatest.prop.PropertyChecks
import org.scalatest.{ Matchers, WordSpec }
import com.github.agourlay.cornichon.util.ShowInstances._

class JsonDiffSpec extends WordSpec with Matchers with PropertyChecks {

  "JsonDiff" must {

    "diff null values" in {
      JsonDiff.diff(Json.Null, Json.Null) should be(Diff(Json.Null, Json.Null, Json.Null))
    }

    "diff null value and something else" in {
      val right = Json.fromString("right")
      JsonDiff.diff(right, Json.Null) should be(Diff(Json.Null, Json.Null, right))
    }

    "diff something and null" in {
      val right = Json.fromString("left")
      JsonDiff.diff(Json.Null, right) should be(Diff(Json.Null, right, Json.Null))
    }

    "diff of identical non nested type" in {
      val left = Json.fromString("left")
      val right = Json.fromString("right")
      JsonDiff.diff(left, right) should be(Diff(right, Json.Null, Json.Null))
    }

    "diff of identical object type" in {
      val input =
        """
          |{
          |"2LettersName" : false,
          | "Age": 50,
          | "Name": "John"
          |}
        """.stripMargin
      val json = CornichonJson.parseJsonUnsafe(input)
      JsonDiff.diff(json, json) should be(Diff(Json.Null, Json.Null, Json.Null))
    }

    "changed object field" in {
      val left =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John"
          |}
        """.stripMargin

      val right =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "Johnny"
          |}
        """.stripMargin

      val jsonLeft = CornichonJson.parseJsonUnsafe(left)
      val jsonRight = CornichonJson.parseJsonUnsafe(right)

      val expected =
        """
          |{ "Name" : "Johnny" }
        """.stripMargin

      val expectedJson = CornichonJson.parseJsonUnsafe(expected)

      JsonDiff.diff(jsonLeft, jsonRight) should be(Diff(expectedJson, Json.Null, Json.Null))
    }

    "deleted object field" in {
      val left =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John"
          |}
        """.stripMargin

      val right =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50
          |}
        """.stripMargin

      val jsonLeft = CornichonJson.parseJsonUnsafe(left)
      val jsonRight = CornichonJson.parseJsonUnsafe(right)

      val expected =
        """
          |{ "Name" : "John" }
        """.stripMargin

      val expectedJson = CornichonJson.parseJsonUnsafe(expected)

      JsonDiff.diff(jsonLeft, jsonRight) should be(Diff(Json.Null, Json.Null, expectedJson))
    }

    "added object field" in {
      val left =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John"
          |}
        """.stripMargin

      val right =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John",
          | "NickName": "Johnny"
          |}
        """.stripMargin

      val jsonLeft = CornichonJson.parseJsonUnsafe(left)
      val jsonRight = CornichonJson.parseJsonUnsafe(right)

      val expected =
        """
          |{ "NickName" : "Johnny" }
        """.stripMargin

      val expectedJson = CornichonJson.parseJsonUnsafe(expected)

      JsonDiff.diff(jsonLeft, jsonRight) should be(Diff(Json.Null, expectedJson, Json.Null))
    }

    "changed object nested field" in {
      val left =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John",
          | "brother": {
          |   "Name" : "Paul",
          |   "Age": 50
          | }
          |}
        """.stripMargin

      val right =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John",
          | "brother": {
          |   "Name" : "Paul",
          |   "Age": 51
          | }
          |}
        """.stripMargin

      val jsonLeft = CornichonJson.parseJsonUnsafe(left)
      val jsonRight = CornichonJson.parseJsonUnsafe(right)

      val expected =
        """
          |{
          |"brother" : {
          |  "Age" : 51
          | }
          |}
        """.stripMargin

      val expectedJson = CornichonJson.parseJsonUnsafe(expected)

      JsonDiff.diff(jsonLeft, jsonRight) should be(Diff(expectedJson, Json.Null, Json.Null))
    }

    "added object nested field" in {
      val left =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John",
          | "brother": {
          |   "Name" : "Paul",
          |   "Age": 50
          | }
          |}
        """.stripMargin

      val right =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John",
          | "brother": {
          |   "Name" : "Paul",
          |   "Age": 50,
          |   "Job": "Farmer"
          | }
          |}
        """.stripMargin

      val jsonLeft = CornichonJson.parseJsonUnsafe(left)
      val jsonRight = CornichonJson.parseJsonUnsafe(right)

      val expected =
        """
          |{
          |"brother" : {
          |  "Job" : "Farmer"
          | }
          |}
        """.stripMargin

      val expectedJson = CornichonJson.parseJsonUnsafe(expected)

      JsonDiff.diff(jsonLeft, jsonRight) should be(Diff(Json.Null, expectedJson, Json.Null))
    }

    "deleted object nested field" in {
      val left =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John",
          | "brother": {
          |   "Name" : "Paul",
          |   "Age": 50
          | }
          |}
        """.stripMargin

      val right =
        """
          |{
          | "2LettersName" : false,
          | "Age": 50,
          | "Name": "John",
          | "brother": {
          |   "Name" : "Paul"
          | }
          |}
        """.stripMargin

      val jsonLeft = CornichonJson.parseJsonUnsafe(left)
      val jsonRight = CornichonJson.parseJsonUnsafe(right)

      val expected =
        """
          |{
          |"brother" : {
          |  "Age" : 50
          | }
          |}
        """.stripMargin

      val expectedJson = CornichonJson.parseJsonUnsafe(expected)

      JsonDiff.diff(jsonLeft, jsonRight) should be(Diff(Json.Null, Json.Null, expectedJson))
    }

    "diff identical arrays" in {
      val one = Json.fromString("one")
      val two = Json.fromString("two")

      val rightArray = Json.fromValues(one :: two :: Nil)
      val leftArray = Json.fromValues(one :: two :: Nil)

      JsonDiff.diff(leftArray, rightArray) should be(Diff(Json.Null, Json.Null, Json.Null))
    }

    "removed element in arrays" in {
      val one = Json.fromString("one")
      val two = Json.fromString("two")
      val three = Json.fromString("three")

      val rightArray = Json.fromValues(one :: two :: Nil)
      val leftArray = Json.fromValues(one :: two :: three :: Nil)

      JsonDiff.diff(leftArray, rightArray) should be(Diff(Json.Null, Json.Null, Json.fromValues(three :: Nil)))
    }

    "added element in arrays" in {
      val one = Json.fromString("one")
      val two = Json.fromString("two")
      val three = Json.fromString("three")

      val leftArray = Json.fromValues(one :: two :: Nil)
      val rightArray = Json.fromValues(one :: two :: three :: Nil)

      JsonDiff.diff(leftArray, rightArray) should be(Diff(Json.Null, Json.fromValues(three :: Nil), Json.Null))
    }

    "changed element in arrays" in {
      val one = Json.fromString("one")
      val two = Json.fromString("two")
      val three = Json.fromString("three")

      val leftArray = Json.fromValues(one :: two :: Nil)
      val rightArray = Json.fromValues(one :: three :: Nil)

      JsonDiff.diff(leftArray, rightArray) should be(Diff(three, Json.Null, Json.Null))
    }

    "changed order in arrays" in {
      val one = Json.fromString("one")
      val two = Json.fromString("two")

      val rightArray = Json.fromValues(one :: two :: Nil)
      val leftArray = Json.fromValues(two :: one :: Nil)

      JsonDiff.diff(leftArray, rightArray) should be(Diff(Json.fromValues(one :: two :: Nil), Json.Null, Json.Null))
    }
  }
}
