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
    case s: String if s.trim.headOption.contains('|') ⇒ parse(DataTableParser.parseDataTable(s).asSprayJson.toString())
    case s: String if s.trim.headOption.contains('{') ⇒ parse(s)
    case s: String if s.trim.headOption.contains('[') ⇒ parse(s)
    case s: String                                    ⇒ JString(s)
    case d: Double                                    ⇒ JDouble(d)
    case b: BigDecimal                                ⇒ JDecimal(b)
    case i: Int                                       ⇒ JInt(i)
    case l: Long                                      ⇒ JLong(l)
    case b: Boolean                                   ⇒ JBool(b)
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

  def filterJsonRootKeys(input: JValue, keys: Seq[String]): JValue =
    keys.foldLeft(input) { (j, k) ⇒
      j.removeField { r ⇒ r._1 == k && (input \ r._1) == r._2 }
    }
}
