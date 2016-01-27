package com.github.agourlay.cornichon.json

import cats.data.Xor
import cats.data.Xor.{ left, right }
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.dsl.DataTableParser
import com.github.agourlay.cornichon.json.CornichonJson.GqlString

import org.json4s._
import org.json4s.jackson.JsonMethods._
import sangria.marshalling.MarshallingUtil._
import sangria.parser.QueryParser
import sangria.marshalling.queryAst._
import sangria.marshalling.json4s.jackson._

import scala.util.{ Failure, Success, Try }

trait CornichonJson {

  def parseJson[A](input: A): JValue = input match {
    case s: String if s.trim.headOption.contains('|') ⇒ parseDataTable(s)
    case s: String if s.trim.headOption.contains('{') ⇒ parse(s)
    case s: String if s.trim.headOption.contains('[') ⇒ parse(s)
    case s: String                                    ⇒ JString(s)
    case d: Double                                    ⇒ JDouble(d)
    case b: BigDecimal                                ⇒ JDecimal(b)
    case i: Int                                       ⇒ JInt(i)
    case l: Long                                      ⇒ JLong(l)
    case b: Boolean                                   ⇒ JBool(b)
    case GqlString(g)                                 ⇒ parseGraphQLJson(g)
  }

  def parseDataTable(table: String): JArray = {
    val sprayArray = DataTableParser.parseDataTable(table).asSprayJson
    JArray(sprayArray.elements.map(v ⇒ parse(v.toString())).toList)
  }

  def parseJsonUnsafe[A](input: A): JValue =
    Try { parseJson(input) } match {
      case Success(json) ⇒ json
      case Failure(e)    ⇒ throw new MalformedJsonError(input, e)
    }

  def parseJsonXor[A](input: A): Xor[CornichonError, JValue] =
    Try { parseJson(input) } match {
      case Success(json) ⇒ right(json)
      case Failure(e)    ⇒ left(new MalformedJsonError(input, e))
    }

  def parseGraphQLJson(input: String) = QueryParser.parseInput(input) match {
    case Success(value) ⇒ value.convertMarshaled[JValue]
    case Failure(e)     ⇒ throw new MalformedGraphQLJsonError(input, e)
  }

  def selectJsonPath(path: JsonPath, json: String): JValue = path.run(parseJson(json))

  def parseArray(input: String): JArray = parseJson(input) match {
    case arr: JArray ⇒ arr
    case _           ⇒ throw new NotAnArrayError(input)
  }

  def selectArrayJsonPath(path: JsonPath, sessionValue: String): JArray = {
    val extracted = selectJsonPath(path, sessionValue)
    extracted match {
      case jarr: JArray ⇒ jarr
      case _            ⇒ throw new NotAnArrayError(extracted)
    }
  }

  // FIXME can break if JSON contains duplicate field => make bulletproof using lenses
  def removeFieldsByPath(input: JValue, paths: Seq[JsonPath]) = {
    paths.foldLeft(input) { (json, path) ⇒
      val jsonToRemove = path.run(json)
      json.removeField { f ⇒ f._1 == path.operations.last.field && f._2 == jsonToRemove }
    }
  }

  def prettyPrint(json: JValue) = pretty(render(json))

  def prettyDiff(first: JValue, second: JValue) = {
    val Diff(changed, added, deleted) = first diff second
    s"""
    |${if (changed == JNothing) "" else "changed = " + prettyPrint(changed)}
    |${if (added == JNothing) "" else "added = " + prettyPrint(added)}
    |${if (deleted == JNothing) "" else "deleted = " + prettyPrint(deleted)}
      """.stripMargin
  }
}

object CornichonJson extends CornichonJson {

  case class GqlString(input: String) extends AnyVal

  implicit class GqlHelper(val sc: StringContext) extends AnyVal {
    def gql(args: Any*): GqlString = {
      val input = sc.s(args: _*)
      GqlString(input)
    }
  }
}
