package com.github.agourlay.cornichon.json

import cats.Show
import cats.instances.string._
import cats.instances.list._
import cats.instances.option._
import cats.syntax.traverse._
import cats.syntax.either._

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.util.Caching
import io.circe.{ ACursor, Json }

import scala.collection.mutable.ListBuffer

case class JsonPath(operations: List[JsonPathOperation]) extends AnyVal {

  def run(superSet: Json): Option[Json] = {
    val (allCursors, projectionMode) = cursors(superSet)
    allCursors.traverse(c ⇒ c.focus).map { focused ⇒
      if (projectionMode)
        Json.fromValues(focused)
      else
        focused.head
    }
  }

  def runStrict(superSet: Json): Either[CornichonError, Json] =
    run(superSet) match {
      case None    ⇒ PathSelectsNothing(JsonPath.showJsonPath.show(this), superSet).asLeft
      case Some(j) ⇒ j.asRight
    }

  def run(json: String): Either[CornichonError, Option[Json]] = parseJson(json).map(run)
  def runStrict(json: String): Either[CornichonError, Json] = parseJson(json).flatMap(runStrict)

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
        // Better be fast here...
        val lb = ListBuffer.empty[ACursor]
        var arrayElementsCursor = arrayFieldCursor.downArray
        var i = 0
        while (i < values.size) {
          lb += arrayElementsCursor
          arrayElementsCursor = arrayElementsCursor.right
          i += 1
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
  private val operationsCache = Caching.buildCache[String, Either[CornichonError, List[JsonPathOperation]]]()

  implicit val showJsonPath: Show[JsonPath] = Show.show[JsonPath] { p ⇒
    p.operations.foldLeft(JsonPath.root)((acc, op) ⇒ s"$acc.${op.pretty}")
  }

  def parse(path: String): Either[CornichonError, JsonPath] =
    if (path == root)
      rightEmptyJsonPath
    else
      operationsCache.get(path, p ⇒ JsonPathParser.parseJsonPath(p)).map(JsonPath(_))

  def run(path: String, json: Json): Either[CornichonError, Option[Json]] =
    JsonPath.parse(path).map(_.run(json))

  def runStrict(path: String, json: Json): Either[CornichonError, Json] =
    JsonPath.parse(path).flatMap(_.runStrict(json))

  def run(path: String, json: String): Either[CornichonError, Option[Json]] =
    for {
      json ← parseJson(json)
      jsonPath ← JsonPath.parse(path)
    } yield jsonPath.run(json)

  def runStrict(path: String, json: String): Either[CornichonError, Json] =
    for {
      json ← parseJson(json)
      jsonPath ← JsonPath.parse(path)
      res ← jsonPath.runStrict(json)
    } yield res
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