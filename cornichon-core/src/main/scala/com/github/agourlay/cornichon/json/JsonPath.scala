package com.github.agourlay.cornichon.json

import cats.Show
import cats.instances.string._
import cats.instances.list._
import cats.instances.option._
import cats.syntax.traverse._
import cats.syntax.either._
import cats.syntax.option._
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.util.Caching
import io.circe.{ ACursor, Json }
import monix.execution.atomic.AtomicBoolean

import scala.collection.mutable.ListBuffer

case class JsonPath(operations: List[JsonPathOperation]) extends AnyVal {

  def run(superSet: Json): Option[Json] = {
    val (allCursors, projectionMode) = cursors(superSet)
    allCursors.traverse(c ⇒ c.focus) match {
      case Some(focused) if projectionMode ⇒ Json.fromValues(focused).some
      case Some(focused)                   ⇒ focused.headOption
      case None if projectionMode          ⇒ Json.fromValues(Nil).some // this choice could be discussed
      case _                               ⇒ None
    }
  }

  def runStrict(superSet: Json): Either[CornichonError, Json] =
    run(superSet) match {
      case Some(j) ⇒ j.asRight
      case None    ⇒ PathSelectsNothing(JsonPath.showJsonPath.show(this), superSet).asLeft
    }

  def run(json: String): Either[CornichonError, Option[Json]] = parseJson(json).map(run)
  def runStrict(json: String): Either[CornichonError, Json] = parseJson(json).flatMap(runStrict)

  // Boolean flag to indicate if the operations contain a valid projection segment.
  // If it is the case, the result must be interpreted as a List otherwise it is always a List of one element.
  private def cursors(input: Json): (List[ACursor], Boolean) = {

    def expandCursors(arrayFieldCursor: ACursor, signalValidProjection: AtomicBoolean): List[ACursor] =
      arrayFieldCursor.values.fold[List[ACursor]](Nil) { values ⇒
        // the projection is valid because there was an array
        signalValidProjection.set(true)
        if (values.isEmpty)
          Nil
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

    // using an AtomicBoolean for signaling...
    val projectionMode = AtomicBoolean(false)
    val cursors = operations.foldLeft[List[ACursor]](input.hcursor :: Nil) { (oc, op) ⇒
      op match {
        case RootSelection                      ⇒ oc
        case FieldSelection(field)              ⇒ oc.map(_.downField(field))
        case RootArrayElementSelection(indice)  ⇒ oc.map(_.downArray.rightN(indice))
        case ArrayFieldSelection(field, indice) ⇒ oc.map(_.downField(field).downArray.rightN(indice))
        case RootArrayFieldProjection           ⇒ oc.flatMap(o ⇒ expandCursors(o, projectionMode))
        case ArrayFieldProjection(field)        ⇒ oc.flatMap(o ⇒ expandCursors(o.downField(field), projectionMode))
      }
    }
    (cursors, projectionMode.get())
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