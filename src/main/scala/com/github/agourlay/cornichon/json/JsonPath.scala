package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.util.Instances._
import io.circe.{ ACursor, Json }
import cats.syntax.either._

case class JsonPath(operations: List[JsonPathOperation] = List.empty) {

  val pretty = operations.foldLeft("$")((acc, op) ⇒ s"$acc.${op.pretty}")

  val isRoot = operations.isEmpty

  def run(superSet: Json): Json = cursor(superSet).focus.getOrElse(Json.Null)
  def run(json: String): Either[CornichonError, Json] = parseJson(json).map(run)

  def cursor(input: Json): ACursor = operations.foldLeft[ACursor](input.hcursor) { (oc, op) ⇒
    op match {
      case FieldSelection(field) ⇒
        oc.downField(field)

      case RootArrayElementSelection(indice) ⇒
        oc.downArray.rightN(indice)

      case ArrayFieldSelection(field, indice) ⇒
        oc.downField(field).downArray.rightN(indice)
    }
  }

  def removeFromJson(input: Json): Json =
    cursor(input).delete.top.getOrElse(input)

}

object JsonPath {
  val root = "$"
  val emptyJsonPath = JsonPath()

  def parse(path: String) = {
    if (path == root) emptyJsonPath
    else {
      val segments = JsonPathParser.parseJsonPath(path)
      fromSegments(segments)
    }
  }

  def run(path: String, json: Json) = JsonPath.parse(path).run(json)

  def run(path: String, json: String) = parseJson(json).map(JsonPath.parse(path).run)

  def fromSegments(segments: List[JsonSegment]) = {
    val operations = segments.map {
      case JsonSegment(field, None)                ⇒ FieldSelection(field)
      case JsonSegment(JsonPath.root, Some(index)) ⇒ RootArrayElementSelection(index)
      case JsonSegment(field, Some(index))         ⇒ ArrayFieldSelection(field, index)
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

case class RootArrayElementSelection(indice: Int) extends JsonPathOperation {
  val field = JsonPath.root
  val pretty = s"$field[$indice]"
}

case class ArrayFieldSelection(field: String, indice: Int) extends JsonPathOperation {
  val pretty = s"$field[$indice]"
}