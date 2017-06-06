package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson._
import io.circe.{ ACursor, Json }
import cats.instances.string._
import cats.syntax.either._

import scala.collection.mutable.ListBuffer

case class JsonPath(operations: List[JsonPathOperation] = List.empty) {

  val pretty = operations.foldLeft(JsonPath.root)((acc, op) ⇒ s"$acc.${op.pretty}")

  val isRoot = operations.isEmpty

  def run(superSet: Json): Json = {
    val focused = cursors(superSet).map(c ⇒ c.focus.getOrElse(Json.Null))
    if (focused.length == 1)
      focused.head
    else
      Json.fromValues(focused)
  }
  def run(json: String): Either[CornichonError, Json] = parseJson(json).map(run)

  def cursors(input: Json): List[ACursor] = operations.foldLeft[List[ACursor]](input.hcursor :: Nil) { (oc, op) ⇒
    op match {
      case RootSelection                      ⇒ oc
      case FieldSelection(field)              ⇒ oc.map(_.downField(field))
      case RootArrayElementSelection(indice)  ⇒ oc.map(_.downArray.rightN(indice))
      case ArrayFieldSelection(field, indice) ⇒ oc.map(_.downField(field).downArray.rightN(indice))
      case ArrayFieldProjection(field) ⇒
        oc.flatMap { o ⇒
          val arrayFieldCursor = o.downField(field)
          arrayFieldCursor.values.fold(arrayFieldCursor :: Nil) { values ⇒
            val lb = ListBuffer.empty[ACursor]
            var arrayElementsCursor = arrayFieldCursor.downArray
            for (_ ← values.indices) {
              lb += arrayElementsCursor
              arrayElementsCursor = arrayElementsCursor.right
            }
            lb.toList
          }
        }
    }
  }

  def removeFromJson(input: Json): Json =
    cursors(input).foldLeft(input) { (j, c) ⇒ c.delete.top.getOrElse(j) }

}

object JsonPath {
  val root = "$"
  val emptyJsonPath = JsonPath()
  private val rightEmptyJsonPath = Right(emptyJsonPath)

  def parse(path: String) = {
    if (path == root) rightEmptyJsonPath
    else {
      val segments = JsonPathParser.parseJsonPath(path)
      segments.map(fromSegments)
    }
  }

  def parseUnsafe(path: String) =
    parse(path).fold(e ⇒ throw e.toException, identity)

  def run(path: String, json: Json) = JsonPath.parse(path).map(_.run(json))

  def run(path: String, json: String) =
    for {
      json ← parseJson(json)
      jsonPath ← JsonPath.parse(path)
    } yield jsonPath.run(json)

  def fromSegments(segments: List[JsonPathSegment]) = {
    val operations = segments.map {
      case JsonFieldSegment(JsonPath.root)              ⇒ RootSelection
      case JsonFieldSegment(field)                      ⇒ FieldSelection(field)
      case JsonArrayIndiceSegment(JsonPath.root, index) ⇒ RootArrayElementSelection(index)
      case JsonArrayIndiceSegment(field, index)         ⇒ ArrayFieldSelection(field, index)
      case JsonArrayProjectionSegment(field)            ⇒ ArrayFieldProjection(field)
    }
    JsonPath(operations)
  }

}

sealed trait JsonPathOperation {
  def field: String
  def pretty: String
}

case object RootSelection extends JsonPathOperation {
  val field = JsonPath.root
  val pretty = field
}

case class FieldSelection(field: String) extends JsonPathOperation {
  val pretty = field
}

case class RootArrayElementSelection(indice: Int) extends JsonPathOperation {
  val field = JsonPath.root
  val pretty = s"$field[$indice]"
}

case class ArrayFieldSelection(field: String, indice: Int) extends JsonPathOperation {
  val pretty = s"$field[$indice]"
}

case class ArrayFieldProjection(field: String) extends JsonPathOperation {
  val pretty = s"$field[*]"
}