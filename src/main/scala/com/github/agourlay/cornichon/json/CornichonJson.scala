package com.github.agourlay.cornichon.json

import cats.Show
import cats.syntax.show._
import cats.data.Xor
import cats.data.Xor.{ left, right }
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.dsl.DataTableParser
import com.github.agourlay.cornichon.json.JsonDiffer.JsonDiff
import com.github.agourlay.cornichon.resolver.Resolvable
import com.github.agourlay.cornichon.util.Instances._
import io.circe.{ Encoder, Json, JsonObject }
import io.circe.syntax._
import sangria.marshalling.MarshallingUtil._
import sangria.parser.QueryParser
import sangria.marshalling.queryAst._
import sangria.marshalling.circe._

import scala.util.{ Failure, Success }

trait CornichonJson {

  def parseJson[A: Encoder: Show](input: A): Xor[CornichonError, Json] = input match {
    case s: String if s.trim.headOption.contains('|') ⇒
      right(Json.fromValues(parseDataTable(s).map(Json.fromJsonObject))) // table
    case s: String if s.trim.headOption.contains('{') ⇒
      parseString(s) // parse object
    case s: String if s.trim.headOption.contains('[') ⇒
      parseString(s) // parse array
    case s: String ⇒
      right(Json.fromString(s))
    case _ ⇒
      Xor.catchNonFatal(input.asJson).leftMap(f ⇒ MalformedJsonError(input.show, f.getMessage))
  }

  def parseJsonUnsafe[A: Encoder: Show](input: A): Json =
    parseJson(input).fold(e ⇒ throw e, identity)

  def parseString(s: String) =
    io.circe.parser.parse(s).leftMap(f ⇒ MalformedJsonError(s, f.message))

  def parseDataTable(table: String): List[JsonObject] =
    DataTableParser.parseDataTable(table).objectList

  def parseGraphQLJson(input: String) = QueryParser.parseInput(input) match {
    case Success(value) ⇒ right(value.convertMarshaled[Json])
    case Failure(e)     ⇒ left(MalformedGraphQLJsonError(input, e))
  }

  def parseGraphQLJsonUnsafe(input: String) =
    parseGraphQLJson(input).fold(e ⇒ throw e, identity)

  def parseArray(input: String): Xor[CornichonError, List[Json]] =
    parseJson(input).flatMap { json ⇒
      json.arrayOrObject(
        left(NotAnArrayError(input)),
        values ⇒ right(values),
        obj ⇒ left(NotAnArrayError(input))
      )
    }

  def selectArrayJsonPath(path: JsonPath, sessionValue: String): Xor[CornichonError, List[Json]] = {
    path.run(sessionValue).flatMap { json ⇒
      json.arrayOrObject(
        left(NotAnArrayError(json)),
        values ⇒ right(values),
        obj ⇒ left(NotAnArrayError(json))
      )
    }
  }

  def removeFieldsByPath(input: Json, paths: Seq[JsonPath]) = {
    paths.foldLeft(input) { (json, path) ⇒
      path.removeFromJson(json)
    }
  }

  def jsonStringValue(j: Json): String =
    j.fold(
      jsonNull = "",
      jsonBoolean = b ⇒ j.show,
      jsonNumber = b ⇒ j.show,
      jsonString = s ⇒ s,
      jsonArray = b ⇒ j.show,
      jsonObject = b ⇒ j.show
    )

  def extract(json: Json, path: String) =
    JsonPath.run(path, json)

  def prettyPrint(json: Json) = json.spaces2

  def diff(first: Json, second: Json): JsonDiff = JsonDiffer.diff(first, second)

  def prettyDiff(first: Json, second: Json) = {
    val JsonDiff(changed, added, deleted) = diff(first, second)

    s"""
    |${if (changed == Json.Null) "" else "changed = " + jsonStringValue(changed)}
    |${if (added == Json.Null) "" else "added = " + jsonStringValue(added)}
    |${if (deleted == Json.Null) "" else "deleted = " + jsonStringValue(deleted)}
      """.stripMargin
  }
}

object CornichonJson extends CornichonJson {

  case class GqlString(input: String)

  object GqlString {

    implicit val gqlResolvableForm = new Resolvable[GqlString] {
      def toResolvableForm(g: GqlString) = g.input
      def fromResolvableForm(s: String) = GqlString(s)
    }

    implicit val gqlShow = new Show[GqlString] {
      def show(g: GqlString) = s"GraphQl JSON ${g.input}"
    }

    implicit val gqlEncode: Encoder[GqlString] = new Encoder[GqlString] {
      final def apply(g: GqlString): Json = parseGraphQLJsonUnsafe(g.input)
    }
  }

  implicit class GqlHelper(val sc: StringContext) extends AnyVal {
    def gql(args: Any*): GqlString = {
      val input = sc.s(args: _*)
      GqlString(input)
    }
  }
}
