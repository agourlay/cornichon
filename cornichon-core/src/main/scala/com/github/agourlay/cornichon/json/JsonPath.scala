package com.github.agourlay.cornichon.json

import cats.Show
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson._
import io.circe.{ ACursor, Json }
import cats.instances.string._
import cats.syntax.either._

import scala.collection.mutable.ListBuffer

case class JsonPath(operations: List[JsonPathOperation] = Nil) extends AnyVal {

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
      case RootArrayFieldProjection           ⇒ oc.flatMap(o ⇒ expandCursors(o))
      case ArrayFieldProjection(field)        ⇒ oc.flatMap(o ⇒ expandCursors(o.downField(field)))
    }
  }

  private def expandCursors(arrayFieldCursor: ACursor) =
    arrayFieldCursor.values.fold(arrayFieldCursor :: Nil) { values ⇒
      if (values.isEmpty)
        arrayFieldCursor :: Nil
      else {
        val lb = ListBuffer.empty[ACursor]
        var arrayElementsCursor = arrayFieldCursor.downArray
        for (_ ← values.indices) {
          lb += arrayElementsCursor
          arrayElementsCursor = arrayElementsCursor.right
        }
        lb.toList
      }
    }

  def removeFromJson(input: Json): Json =
    cursors(input).foldLeft(input) { (j, c) ⇒ c.delete.top.getOrElse(j) }

}

object JsonPath {
  val root = "$"
  private val rightEmptyJsonPath = Right(JsonPath())

  implicit val showJsonPath = Show.show[JsonPath] { p ⇒
    p.operations.foldLeft(JsonPath.root)((acc, op) ⇒ s"$acc.${op.pretty}")
  }

  def parse(path: String) =
    if (path == root)
      rightEmptyJsonPath
    else {
      val segments = JsonPathParser.parseJsonPath(path)
      segments.map(fromSegments)
    }

  def run(path: String, json: Json) = JsonPath.parse(path).map(_.run(json))

  def run(path: String, json: String) =
    for {
      json ← parseJson(json)
      jsonPath ← JsonPath.parse(path)
    } yield jsonPath.run(json)

  def fromSegments(segments: List[JsonPathSegment]) =
    JsonPath(
      segments.map {
        case FieldSegment(JsonPath.root, None)        ⇒ RootSelection
        case FieldSegment(JsonPath.root, Some(index)) ⇒ RootArrayElementSelection(index)
        case FieldSegment(field, None)                ⇒ FieldSelection(field)
        case FieldSegment(field, Some(index))         ⇒ ArrayFieldSelection(field, index)
        case ArrayProjectionSegment(JsonPath.root)    ⇒ RootArrayFieldProjection
        case ArrayProjectionSegment(field)            ⇒ ArrayFieldProjection(field)
      }
    )

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

case object RootArrayFieldProjection extends JsonPathOperation {
  val field = JsonPath.root
  val pretty = s"$field[*]"
}