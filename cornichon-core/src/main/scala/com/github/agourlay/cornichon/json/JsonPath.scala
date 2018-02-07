package com.github.agourlay.cornichon.json

import cats.Show
import cats.instances.string._
import cats.syntax.either._

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson._

import io.circe.{ ACursor, Json }

import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ListBuffer

case class JsonPath(operations: List[JsonPathOperation]) extends AnyVal {

  def run(superSet: Json): Json = {
    val (allCursors, projectionMode) = cursors(superSet)
    val focused = allCursors.map(c ⇒ c.focus.getOrElse(Json.Null))
    if (projectionMode)
      Json.fromValues(focused)
    else
      focused.head
  }

  def run(json: String): Either[CornichonError, Json] = parseJson(json).map(run)

  // Boolean flag to indicate if the operations contain a projection segment.
  // If it is the case, the result must be interpreted as a List.
  // Otherwise it is always a List of one element.
  def cursors(input: Json): (List[ACursor], Boolean) = {
    val cursors = operations.foldLeft[List[ACursor]](input.hcursor :: Nil) { (oc, op) ⇒
      op match {
        case RootSelection                      ⇒ oc
        case FieldSelection(field)              ⇒ oc.map(_.downField(field))
        case RootArrayElementSelection(indice)  ⇒ oc.map(_.downArray.rightN(indice))
        case ArrayFieldSelection(field, indice) ⇒ oc.map(_.downField(field).downArray.rightN(indice))
        case RootArrayFieldProjection           ⇒ oc.flatMap(o ⇒ expandCursors(o))
        case ArrayFieldProjection(field)        ⇒ oc.flatMap(o ⇒ expandCursors(o.downField(field)))
      }
    }
    (cursors, operations.exists(_.projection))
  }

  private def expandCursors(arrayFieldCursor: ACursor) =
    arrayFieldCursor.values.fold(arrayFieldCursor :: Nil) { values ⇒
      if (values.isEmpty)
        arrayFieldCursor :: Nil
      else {
        val lb = ListBuffer.empty[ACursor]
        var arrayElementsCursor = arrayFieldCursor.downArray
        for (_ ← values.seq) {
          lb += arrayElementsCursor
          arrayElementsCursor = arrayElementsCursor.right
        }
        lb.toList
      }
    }

  def removeFromJson(input: Json): Json =
    cursors(input)._1.foldLeft(input) { (j, c) ⇒
      c.focus match {
        case None    ⇒ j // path does not exist in input
        case Some(_) ⇒ c.delete.top.getOrElse(Json.Null) //drop path and back to top
      }
    }

}

object JsonPath {
  val root = "$"
  val rootPath = JsonPath(Nil)
  private val rightEmptyJsonPath = Right(rootPath)
  private val operationsCache = TrieMap.empty[String, Either[CornichonError, List[JsonPathOperation]]]

  implicit val showJsonPath = Show.show[JsonPath] { p ⇒
    p.operations.foldLeft(JsonPath.root)((acc, op) ⇒ s"$acc.${op.pretty}")
  }

  def parse(path: String): Either[CornichonError, JsonPath] =
    if (path == root)
      rightEmptyJsonPath
    else
      operationsCache.getOrElseUpdate(path, JsonPathParser.parseJsonPath(path)).map(JsonPath(_))

  def run(path: String, json: Json): Either[CornichonError, Json] =
    JsonPath.parse(path).map(_.run(json))

  def run(path: String, json: String): Either[CornichonError, Json] =
    for {
      json ← parseJson(json)
      jsonPath ← JsonPath.parse(path)
    } yield jsonPath.run(json)

}

sealed trait JsonPathOperation {
  def field: String
  def pretty: String
  def projection: Boolean = false
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
  override val projection = true
}

case object RootArrayFieldProjection extends JsonPathOperation {
  val field = JsonPath.root
  val pretty = s"$field[*]"
  override val projection = true
}