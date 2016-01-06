package com.github.agourlay.cornichon.json

import cats.data.Xor
import cats.data.Xor.{ left, right }
import com.github.agourlay.cornichon.core.{ CornichonError, MalformedJsonError }
import com.github.agourlay.cornichon.dsl.DataTableParser

import org.json4s._
import org.json4s.jackson.JsonMethods._

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

  def selectJsonPath(path: String, json: JValue) = {
    val parsedJsonPath = JsonPathParser.parseJsonPath(path)
    val jsonPath = JsonPath.fromSegments(parsedJsonPath)
    jsonPath.operations.foldLeft(json) { (j, op) ⇒ op.run(j) }
  }

  // FIXME can break if JSON contains duplicate field => make bulletproof using lenses
  def removeFieldsByPath(input: JValue, paths: Seq[String]) = {
    paths.foldLeft(input) { (json, path) ⇒
      val parsedJsonPath = JsonPathParser.parseJsonPath(path)
      val jsonToRemove = selectJsonPath(path, json)
      json.removeField { f ⇒ f._1 == parsedJsonPath.last.field && f._2 == jsonToRemove }
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

}
