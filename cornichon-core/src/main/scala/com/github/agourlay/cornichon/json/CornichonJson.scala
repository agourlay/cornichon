package com.github.agourlay.cornichon.json

import cats.Show
import cats.syntax.show._
import cats.syntax.either._
import cats.instances.string._
import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.either._
import com.github.agourlay.cornichon.core.{ CornichonError, Session }
import com.github.agourlay.cornichon.dsl.DataTableParser
import gnieh.diffson.circe._
import io.circe._
import io.circe.syntax._
import sangria.marshalling.MarshallingUtil._
import sangria.parser.QueryParser
import sangria.marshalling.queryAst._
import sangria.marshalling.circe._

import scala.util.{ Failure, Success }

trait CornichonJson {

  def parseJson[A: Encoder: Show](input: A): Either[CornichonError, Json] = input match {
    case s: String ⇒
      val trimmed = s.trim
      if (trimmed.isEmpty)
        Right(Json.fromString(s))
      else {
        val firstChar = trimmed.head
        if (firstChar == '{' || firstChar == '[')
          parseString(s) // parse object or array
        else if (firstChar == '|')
          parseDataTable(s).map(list ⇒ Json.fromValues(list.map(Json.fromJsonObject))) // table
        else
          Right(Json.fromString(s)) // treated as a String
      }
    case _ ⇒
      Either.catchNonFatal(input.asJson).leftMap(f ⇒ MalformedJsonError(input.show, f.getMessage))
  }

  def parseJsonUnsafe[A: Encoder: Show](input: A): Json =
    parseJson(input).valueUnsafe

  def parseString(s: String): Either[MalformedJsonError[String], Json] =
    io.circe.parser.parse(s).leftMap(f ⇒ MalformedJsonError(s, f.message))

  def isJsonString(s: String): Boolean = {
    val trimmed = s.trim
    if (trimmed.isEmpty)
      false
    else trimmed.head != '[' && trimmed.head != '{' && trimmed.head != '|'
  }

  def parseDataTable(table: String): Either[CornichonError, List[JsonObject]] = {
    def parseCol(col: (String, String)) = parseString(col._2).map(col._1 → _)
    def parseRow(row: Map[String, String]) = row.toList.traverse(parseCol) map JsonObject.fromIterable

    parseDataTableRaw(table).flatMap(_.traverse(parseRow))
  }

  def parseDataTableRaw(table: String): Either[CornichonError, List[Map[String, String]]] =
    DataTableParser.parse(table).map(_.rawStringList)

  def parseGraphQLJson(input: String): Either[MalformedGraphQLJsonError[String], Json] =
    QueryParser.parseInput(input) match {
      case Success(value) ⇒ Right(value.convertMarshaled[Json])
      case Failure(e)     ⇒ Left(MalformedGraphQLJsonError(input, e))
    }

  def jsonArrayValues(json: Json): Either[CornichonError, Vector[Json]] =
    json.asArray.map(Right(_)).getOrElse(Left(NotAnArrayError(json)))

  def parseArray(input: String): Either[CornichonError, Vector[Json]] =
    parseJson(input).flatMap(jsonArrayValues)

  def selectArrayJsonPath(path: JsonPath, json: String): Either[CornichonError, Vector[Json]] =
    path.run(json).flatMap(jsonArrayValues)

  def removeFieldsByPath(input: Json, paths: Seq[JsonPath]): Json =
    paths.foldLeft(input) { (json, path) ⇒
      path.removeFromJson(json)
    }

  def jsonStringValue(j: Json): String =
    // Use Json.Folder for performance https://github.com/circe/circe/pull/656
    j.foldWith(
      new Json.Folder[String] {
        def onNull: String = ""
        def onBoolean(value: Boolean): String = j.show
        def onNumber(value: JsonNumber): String = j.show
        def onString(value: String): String = value
        def onArray(value: Vector[Json]): String = j.show
        def onObject(value: JsonObject): String = j.show
      }
    )

  def extract(json: Json, path: String): Either[CornichonError, Json] =
    JsonPath.run(path, json)

  def prettyPrint(json: Json): String =
    json.spaces2

  def diffPatch(first: Json, second: Json): JsonPatch =
    JsonDiff.diff(first, second, remember = true)

  def decodeAs[A: Decoder](json: Json): Either[CornichonError, A] =
    json.as[A].leftMap(df ⇒ JsonDecodingFailure(json, df.message))

  def whitelistingValue(first: Json, second: Json): Either[WhitelistingError, Json] = {
    val diff = diffPatch(first, second)
    val forbiddenPatchOps = diff.ops.collect { case r: Remove ⇒ r }
    if (forbiddenPatchOps.isEmpty) {
      val addOps = diff.ops.collect { case r: Add ⇒ r }
      Right(JsonPatch(addOps)(first))
    } else
      Left(WhitelistingError(forbiddenPatchOps.map(_.path.toString), second))
  }

  def findAllJsonWithValue(values: List[String], json: Json): Vector[JsonPath] = {
    def keyValues(currentPath: String, json: Json, level: Int): Vector[(String, Json)] = {

      def leafValue() = if (level == 0) Vector((currentPath, json)) else Vector.empty

      // Use Json.Folder for performance https://github.com/circe/circe/pull/656
      json.foldWith(
        new Json.Folder[Vector[(String, Json)]] {
          def onNull = Vector.empty
          def onBoolean(value: Boolean) = leafValue()
          def onNumber(value: JsonNumber) = leafValue()
          def onString(value: String) = leafValue()
          def onArray(elems: Vector[Json]) = elems.zipWithIndex.flatMap { case (e, indice) ⇒ keyValuesHelper(s"$currentPath[$indice]", e, level) }
          def onObject(elems: JsonObject) = elems.toVector.flatMap { case (k, v) ⇒ keyValuesHelper(s"$currentPath.$k", v, level) }
        }
      )
    }

    def keyValuesHelper(key: String, value: Json, level: Int): Vector[(String, Json)] =
      (key, value) +: keyValues(key, value, level + 1)

    // Do not traverse the JSON if there are no values to find
    if (values.nonEmpty)
      keyValues(JsonPath.root, json, level = 0).collect { case (k, v) if values.exists(v.asString.contains) ⇒ JsonPath.parse(k).valueUnsafe }
    else
      Vector.empty
  }
}

object CornichonJson extends CornichonJson {

  implicit class sessionJson(val s: Session) {
    def getJson(key: String, stackingIndice: Option[Int] = None, path: String = JsonPath.root): Either[CornichonError, Json] =
      for {
        sessionValue ← s.get(key, stackingIndice)
        jsonValue ← parseJson(sessionValue)
        extracted ← JsonPath.run(path, jsonValue)
      } yield extracted

    def getJsonStringField(key: String, stackingIndice: Option[Int] = None, path: String = JsonPath.root): Either[CornichonError, String] =
      for {
        json ← getJson(key, stackingIndice, path)
        field ← Either.fromOption(json.asString, NotStringFieldError(json, path))
      } yield field

    def getJsonStringFieldUnsafe(key: String, stackingIndice: Option[Int] = None, path: String = JsonPath.root): String =
      getJsonStringField(key, stackingIndice, path).valueUnsafe

    def getJsonOpt(key: String, stackingIndice: Option[Int] = None): Option[Json] =
      s.getOpt(key, stackingIndice).flatMap(s ⇒ parseJson(s).toOption)
  }

  implicit class GqlHelper(val sc: StringContext) extends AnyVal {
    def gqljson(args: Any*): GqlString = {
      val input = sc.s(args: _*)
      GqlString(input)
    }
  }
}
