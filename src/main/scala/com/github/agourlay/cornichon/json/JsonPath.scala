package com.github.agourlay.cornichon.json

import org.json4s.JsonAST.JValue
import com.github.agourlay.cornichon.json.CornichonJson._

case class JsonPath(operations: List[JsonPathOperation] = List.empty) {

  val pretty = operations.foldLeft("$")((acc, op) ⇒ s"$acc.${op.pretty}")

  val isRoot = operations.isEmpty

  def run(json: JValue): JValue = operations.foldLeft(json) { (j, op) ⇒ op.run(j) }

  def run(json: String): JValue = run(parseJson(json))
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

  def run(path: String, json: JValue) = JsonPath.parse(path).run(json)

  def run(path: String, json: String) = JsonPath.parse(path).run(parseJson(json))

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
  def run(json: JValue): JValue
}

case class FieldSelection(field: String) extends JsonPathOperation {
  def run(json: JValue) = json \ field
  val pretty = field
}

case class ArrayFieldSelection(field: String, indice: Int) extends JsonPathOperation {
  def run(json: JValue) = (json \ field)(indice)
  val pretty = s"$field[$indice]"
}