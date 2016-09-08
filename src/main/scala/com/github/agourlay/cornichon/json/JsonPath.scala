package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.util.ShowInstances._
import io.circe.Json
import cats.data.Xor

case class JsonPath(operations: List[JsonPathOperation] = List.empty) {

  val pretty = operations.foldLeft("$")((acc, op) ⇒ s"$acc.${op.pretty}")

  val isRoot = operations.isEmpty

  def run(superSet: Json): Json = cursor(superSet).fold(Json.Null)(c ⇒ c.focus)
  def run(json: String): Xor[CornichonError, Json] = parseJson(json).map(run)

  def cursor(input: Json) = operations.foldLeft(Option(input.cursor)) { (oc, op) ⇒
    op match {
      case FieldSelection(field) ⇒
        for {
          c ← oc
          downC ← c.downField(field)
        } yield downC

      case RootArrayElementSelection(indice) ⇒
        for {
          c ← oc
          arrayC ← c.downArray
          indexC ← arrayC.rightN(indice)
        } yield indexC

      case ArrayFieldSelection(field, indice) ⇒
        for {
          c ← oc
          downC ← c.downField(field)
          arrayC ← downC.downArray
          indexC ← arrayC.rightN(indice)
        } yield indexC
    }
  }

  def removeFromJson(input: Json): Json =
    cursor(input).fold(input) { c ⇒
      c.delete.fold(input)(_.top)
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