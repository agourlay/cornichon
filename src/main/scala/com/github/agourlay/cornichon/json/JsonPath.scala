package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson._
import io.circe.Json
import cats.data.Xor

case class JsonPath(operations: List[JsonPathOperation] = List.empty) {

  val pretty = operations.foldLeft("$")((acc, op) ⇒ s"$acc.${op.pretty}")

  val isRoot = operations.isEmpty

  //FIXME should we use Lens or Cursor to implement JsonPath?
  //FIXME current implement does not work
  //TODO test with key starting with numbers
  /*private val lense = operations.foldLeft(io.circe.optics.JsonPath.root) { (l, op) ⇒
    op match {
      case FieldSelection(field) ⇒
        l.field
      case ArrayFieldSelection(field, indice) ⇒
        l.field.at(indice)
    }
  }*/

  def run(superSet: Json): Json = cursor(superSet).fold(Json.Null)(c ⇒ c.focus)
  def run(json: String): Xor[CornichonError, Json] = parseJson(json).map(run)

  def cursor(input: Json) = operations.foldLeft(Option(input.cursor)) { (oc, op) ⇒
    op match {
      case FieldSelection(field) ⇒
        for {
          c ← oc
          downC ← c.downField(field)
        } yield downC

      case ArrayFieldSelection(field, indice) ⇒
        for {
          c ← oc
          downC ← c.downField(field)
          arrayC ← downC.downArray
          indexC ← arrayC.rightN(indice)
        } yield indexC
    }
  }

  def removeFromJson(input: Json): Json = {
    cursor(input).fold(input) { c ⇒
      c.delete.fold(input)(_.top)
    }
  }
}

object JsonPath {
  val root = "$"

  def parse(path: String) = {
    if (path == root) JsonPath()
    else {
      val segments = JsonPathParser.parseJsonPath(path)
      fromSegments(segments)
    }
  }

  def run(path: String, json: Json) = JsonPath.parse(path).run(json)

  def run(path: String, json: String) = parseJson(json).map(JsonPath.parse(path).run)

  def fromSegments(segments: List[JsonSegment]) = {
    val operations = segments.map {
      case JsonSegment(field, None)        ⇒ FieldSelection(field)
      case JsonSegment(field, Some(index)) ⇒ ArrayFieldSelection(field, index)
    }
    JsonPath(operations)
  }

}

sealed trait JsonPathOperation {
  def field: String
  def pretty: String
}

case class FieldSelection(field: String) extends JsonPathOperation {
  val pretty = field
}

case class ArrayFieldSelection(field: String, indice: Int) extends JsonPathOperation {
  val pretty = s"$field[$indice]"
}