package com.github.agourlay.cornichon.json

import cats.Show
import cats.syntax.show._
import cats.syntax.either._
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.dsl.DataTableParser
import com.github.agourlay.cornichon.resolver.Resolvable
import com.github.agourlay.cornichon.util.Instances._
import gnieh.diffson.circe._
import io.circe.{ Encoder, Json, JsonObject }
import io.circe.syntax._
import sangria.marshalling.MarshallingUtil._
import sangria.parser.QueryParser
import sangria.marshalling.queryAst._
import sangria.marshalling.circe._

import scala.util.{ Failure, Success }

trait CornichonJson {

  def parseJson[A: Encoder: Show](input: A): Either[CornichonError, Json] = input match {
    case s: String ⇒
      s.trim.headOption.fold(Either.right[CornichonError, Json](Json.fromString(s))) { firstChar ⇒
        if (firstChar == '{' || firstChar == '[')
          parseString(s) // parse object or array
        else if (firstChar == '|')
          Right(Json.fromValues(parseDataTable(s).map(Json.fromJsonObject))) // table
        else
          Right(Json.fromString(s)) // treated as a String
      }
    case _ ⇒
      Either.catchNonFatal(input.asJson).leftMap(f ⇒ MalformedJsonError(input.show, f.getMessage))
  }

  def parseJsonUnsafe[A: Encoder: Show](input: A): Json =
    parseJson(input).fold(e ⇒ throw e, identity)

  def parseString(s: String) =
    io.circe.parser.parse(s).leftMap(f ⇒ MalformedJsonError(s, f.message))

  def parseDataTable(table: String): List[JsonObject] =
    DataTableParser.parseDataTable(table).objectList

  def parseGraphQLJson(input: String) = QueryParser.parseInput(input) match {
    case Success(value) ⇒ Right(value.convertMarshaled[Json])
    case Failure(e)     ⇒ Left(MalformedGraphQLJsonError(input, e))
  }

  def parseGraphQLJsonUnsafe(input: String) =
    parseGraphQLJson(input).fold(e ⇒ throw e, identity)

  def jsonArrayValues(json: Json): Either[CornichonError, List[Json]] =
    json.arrayOrObject(
      Left(NotAnArrayError(json)),
      values ⇒ Right(values),
      _ ⇒ Left(NotAnArrayError(json))
    )

  def parseArray(input: String): Either[CornichonError, List[Json]] =
    parseJson(input).flatMap(jsonArrayValues)

  def selectArrayJsonPath(path: JsonPath, sessionValue: String): Either[CornichonError, List[Json]] =
    path.run(sessionValue).flatMap(jsonArrayValues)

  def removeFieldsByPath(input: Json, paths: Seq[JsonPath]) =
    paths.foldLeft(input) { (json, path) ⇒
      path.removeFromJson(json)
    }

  def jsonStringValue(j: Json): String =
    j.fold(
      jsonNull = "",
      jsonBoolean = _ ⇒ j.show,
      jsonNumber = _ ⇒ j.show,
      jsonString = s ⇒ s,
      jsonArray = _ ⇒ j.show,
      jsonObject = _ ⇒ j.show
    )

  def extract(json: Json, path: String) =
    JsonPath.run(path, json)

  def prettyPrint(json: Json) = json.spaces2

  def diffPatch(first: Json, second: Json): JsonPatch = JsonDiff.diff(first, second, remember = true)

  def whitelistingValue(first: Json, second: Json): Either[WhitelistingError, Json] = {
    val diff = diffPatch(first, second)
    val forbiddenPatchOps = diff.ops.collect { case r: Remove ⇒ r }
    val addOps = diff.ops.collect { case r: Add ⇒ r }
    if (forbiddenPatchOps.isEmpty) Right(JsonPatch(addOps)(first))
    else Left(WhitelistingError(forbiddenPatchOps.map(_.path.toString), second))
  }

  def findAllJsonWithValue(values: List[String], json: Json): List[(String, Json)] = {
    def keyValues(currentPath: String, json: Json): List[(String, Json)] =
      json.fold(
        jsonNull = Nil,
        jsonBoolean = _ ⇒ Nil,
        jsonNumber = _ ⇒ Nil,
        jsonString = _ ⇒ Nil,
        jsonArray = elems ⇒ elems.zipWithIndex.flatMap { case (e, indice) ⇒ keyValuesHelper(s"$currentPath[$indice]", e) },
        jsonObject = elems ⇒ elems.toList.flatMap { case (k, v) ⇒ keyValuesHelper(s"$currentPath.$k", v) }
      )

    def keyValuesHelper(key: String, value: Json): List[(String, Json)] =
      (key, value) :: keyValues(key, value)

    // Do not traverse the JSON if there are no values to find
    if (values.nonEmpty)
      keyValues(JsonPath.root, json).collect { case (k, v) if values.exists(v.asString.contains) ⇒ (k, v) }
    else
      Nil
  }
}

object CornichonJson extends CornichonJson {

  case class GqlString(input: String) extends AnyVal

  object GqlString {

    implicit val gqlResolvableForm = new Resolvable[GqlString] {
      def toResolvableForm(g: GqlString) = g.input
      def fromResolvableForm(s: String) = GqlString(s)
    }

    implicit val gqlShow =
      Show.show[GqlString](g ⇒ s"GraphQl JSON ${g.input}")

    implicit val gqlEncode =
      Encoder.instance[GqlString](g ⇒ parseGraphQLJsonUnsafe(g.input))
  }

  implicit class GqlHelper(val sc: StringContext) extends AnyVal {
    def gql(args: Any*): GqlString = {
      val input = sc.s(args: _*)
      GqlString(input)
    }
  }
}
