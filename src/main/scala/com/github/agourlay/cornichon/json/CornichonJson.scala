package com.github.agourlay.cornichon.json

import cats.data.Xor
import cats.data.Xor.{ left, right }
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.dsl.DataTableParser
import com.github.agourlay.cornichon.json.CornichonJson.GqlString
import io.circe.{ Json, JsonObject }
import org.json4s.JsonAST.JNothing
import sangria.marshalling.MarshallingUtil._
import sangria.parser.QueryParser
import sangria.marshalling.queryAst._
import sangria.marshalling.circe._

import scala.util.{ Failure, Success }

trait CornichonJson {

  def parseJson[A](input: A): Xor[CornichonError, Json] = input match {
    case s: String if s.trim.headOption.contains('|') ⇒ right(Json.fromValues(parseDataTable(s).map(Json.fromJsonObject)))
    case s: String if s.trim.headOption.contains('{') ⇒ parseString(s)
    case s: String if s.trim.headOption.contains('[') ⇒ parseString(s)
    case s: String                                    ⇒ right(Json.fromString(s))
    case d: Double                                    ⇒ right(Json.fromDoubleOrNull(d))
    case b: BigDecimal                                ⇒ right(Json.fromBigDecimal(b))
    case i: Int                                       ⇒ right(Json.fromInt(i))
    case l: Long                                      ⇒ right(Json.fromLong(l))
    case b: Boolean                                   ⇒ right(Json.fromBoolean(b))
    case GqlString(g)                                 ⇒ parseGraphQLJson(g)
  }

  def parseJsonUnsafe[A](input: A): Json =
    parseJson(input).fold(e ⇒ throw e, identity)

  def parseString(s: String) =
    io.circe.parser.parse(s).leftMap(f ⇒ new MalformedJsonError(s, f.message))

  def parseDataTable(table: String): List[JsonObject] =
    DataTableParser.parseDataTable(table).objectList

  def parseGraphQLJson(input: String) = QueryParser.parseInput(input) match {
    case Success(value) ⇒ right(value.convertMarshaled[Json])
    case Failure(e)     ⇒ left(new MalformedGraphQLJsonError(input, e))
  }

  def parseArray(input: String): Xor[CornichonError, List[Json]] =
    parseJson(input).flatMap { json ⇒
      json.arrayOrObject(
        left(new NotAnArrayError(input)),
        values ⇒ right(values),
        obj ⇒ left(new NotAnArrayError(input))
      )
    }

  def selectArrayJsonPath(path: JsonPath, sessionValue: String): Xor[CornichonError, List[Json]] = {
    path.run(sessionValue).flatMap { json ⇒
      json.arrayOrObject(
        left(new NotAnArrayError(json)),
        values ⇒ right(values),
        obj ⇒ left(new NotAnArrayError(json))
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
      jsonBoolean = b ⇒ prettyPrint(j),
      jsonNumber = b ⇒ prettyPrint(j),
      jsonString = s ⇒ s,
      jsonArray = b ⇒ prettyPrint(j),
      jsonObject = b ⇒ prettyPrint(j)
    )

  def extract(json: Json, path: String) =
    JsonPath.run(path, json)

  def prettyPrint(json: Json) = json.spaces2

  def prettyDiff(first: Json, second: Json) = {
    val Diff(changed, added, deleted) = diff(first, second)

    s"""
    |${if (changed == Json.Null) "" else "changed = " + jsonStringValue(changed)}
    |${if (added == Json.Null) "" else "added = " + jsonStringValue(added)}
    |${if (deleted == Json.Null) "" else "deleted = " + jsonStringValue(deleted)}
      """.stripMargin
  }

  //FIXME implement JSON diff independently from JSON4s
  def diff(v1: Json, v2: Json): Diff = {
    import org.json4s.JValue
    import org.json4s.jackson.JsonMethods._

    def circeToJson4s(c: Json): JValue = c match {
      case Json.Null ⇒ JNothing
      case _         ⇒ parse(c.noSpaces)
    }

    def json4sToCirce(j: JValue): Json = j match {
      case JNothing ⇒ Json.Null
      case _        ⇒ parseJsonUnsafe(compact(render(j)))
    }

    val diff = circeToJson4s(v1).diff(circeToJson4s(v2))

    Diff(changed = json4sToCirce(diff.changed), added = json4sToCirce(diff.added), deleted = json4sToCirce(diff.deleted))
  }

  case class Diff(changed: Json, added: Json, deleted: Json)
}

object CornichonJson extends CornichonJson {

  case class GqlString(input: String) {
    override val toString = s"GraphQl JSON $input"
  }

  implicit class GqlHelper(val sc: StringContext) extends AnyVal {
    def gql(args: Any*): GqlString = {
      val input = sc.s(args: _*)
      GqlString(input)
    }
  }
}
