package com.github.agourlay.cornichon.json

import cats.Show
import cats.syntax.option._
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.util.TraverseUtils.traverseLO
import io.circe.{ ACursor, Json }
import scala.collection.mutable.ListBuffer

case class JsonPath(operations: Vector[JsonPathOperation]) extends AnyVal {

  def run(superSet: Json): Option[Json] = {
    val (allCursors, projectionMode) = cursors(superSet)
    allCursors match {
      case head :: Nil if !projectionMode =>
        // fast path for single cursor without projection
        head.focus
      case _ =>
        traverseLO(allCursors)(c => c.focus) match {
          case Some(focused) if projectionMode => Json.fromValues(focused).some
          case Some(focused)                   => focused.headOption
          case None if projectionMode          => Json.fromValues(Nil).some // this choice could be discussed
          case _                               => None
        }
    }
  }

  def runStrict(superSet: Json): Either[CornichonError, Json] =
    run(superSet) match {
      case Some(j) => Right(j)
      case None    => Left(PathSelectsNothing(JsonPath.show.show(this), superSet))
    }

  def run(json: String): Either[CornichonError, Option[Json]] = parseDslJson(json).map(run)
  def runStrict(json: String): Either[CornichonError, Json] = parseDslJson(json).flatMap(runStrict)

  // Boolean flag to indicate if the operations contain a valid projection segment.
  // If it is the case, the result must be interpreted as a List otherwise it is always a List of one element.
  private def cursors(input: Json): (List[ACursor], Boolean) = {
    // captured in def to avoid passing it around
    var projectionMode = false

    def expandCursors(arrayFieldCursor: ACursor): List[ACursor] =
      arrayFieldCursor.values match {
        case None => Nil
        case Some(values) =>
          // the projection is valid because there was an array
          projectionMode = true
          val size = values.size
          if (size == 0)
            Nil
          else {
            val lb = ListBuffer.empty[ACursor]
            var arrayElementsCursor = arrayFieldCursor.downArray
            var i = 0
            while (i < size) {
              lb += arrayElementsCursor
              arrayElementsCursor = arrayElementsCursor.right
              i += 1
            }
            lb.toList
          }
      }

    val operationsLen = operations.length
    var i = 0
    var acc: List[ACursor] = input.hcursor :: Nil
    while (i < operationsLen) {
      operations(i) match {
        case RootSelection =>
        // do nothing
        case FieldSelection(field) =>
          acc = acc.map(_.downField(field))
        case RootArrayElementSelection(index) =>
          acc = acc.map(_.downN(index))
        case ArrayFieldSelection(field, index) =>
          acc = acc.map(_.downField(field).downN(index))
        case RootArrayFieldProjection =>
          acc = acc.flatMap(o => expandCursors(o))
        case ArrayFieldProjection(field) =>
          acc = acc.flatMap(o => expandCursors(o.downField(field)))
      }
      i += 1
    }
    (acc, projectionMode)
  }

  def removeFromJson(input: Json): Json =
    cursors(input)._1.foldLeft(input) { (j, c) =>
      if (c.succeeded) {
        c.delete.top.getOrElse(Json.Null) // drop path and back to top
      } else {
        j // path does not exist in input
      }
    }

}

object JsonPath {
  protected[cornichon] val root = "$"
  protected[cornichon] val rootPath = JsonPath(Vector.empty)
  private val rightEmptyJsonPath = Right(rootPath)

  implicit val show: Show[JsonPath] = Show.show[JsonPath] { p =>
    p.operations.iterator.map {
      case RootSelection                     => root
      case FieldSelection(field)             => field
      case RootArrayElementSelection(index)  => s"$root[$index]"
      case ArrayFieldSelection(field, index) => s"$field[$index]"
      case RootArrayFieldProjection          => s"$root[*]"
      case ArrayFieldProjection(field)       => s"$field[*]"
    }.mkString(".")
  }

  def parse(path: String): Either[CornichonError, JsonPath] =
    if (path == root)
      rightEmptyJsonPath
    else
      JsonPathParser.parseJsonPath(path).map(JsonPath(_))

  def run(path: String, json: Json): Either[CornichonError, Option[Json]] =
    JsonPath.parse(path).map(_.run(json))

  def runStrict(path: String, json: Json): Either[CornichonError, Json] =
    JsonPath.parse(path).flatMap(_.runStrict(json))

  def run(path: String, json: String): Either[CornichonError, Option[Json]] =
    for {
      json <- parseDslJson(json)
      jsonPath <- JsonPath.parse(path)
    } yield jsonPath.run(json)

  def runStrict(path: String, json: String): Either[CornichonError, Json] =
    for {
      json <- parseDslJson(json)
      jsonPath <- JsonPath.parse(path)
      res <- jsonPath.runStrict(json)
    } yield res
}

sealed trait JsonPathOperation
case object RootSelection extends JsonPathOperation
case class FieldSelection(field: String) extends JsonPathOperation
case class RootArrayElementSelection(index: Int) extends JsonPathOperation
case class ArrayFieldSelection(field: String, index: Int) extends JsonPathOperation
case class ArrayFieldProjection(field: String) extends JsonPathOperation
case object RootArrayFieldProjection extends JsonPathOperation