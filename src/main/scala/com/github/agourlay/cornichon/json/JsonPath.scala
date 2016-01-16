package com.github.agourlay.cornichon.json

import org.json4s.JsonAST.JValue

case class JsonPath(path: String, operations: List[JsonPathOperation])

object JsonPath {
  def fromSegments(segments: List[JsonSegment]) = {
    val path = segments.map(_.fullKey).mkString(".")
    val operations = segments.map {
      case JsonSegment(field, None)        ⇒ FieldSelection(field)
      case JsonSegment(field, Some(index)) ⇒ ArrayFieldSelection(field, index)
    }
    JsonPath(path, operations)
  }
  val root = "$"
}

sealed trait JsonPathOperation {
  def run(json: JValue): JValue
}

case class FieldSelection(field: String) extends JsonPathOperation {
  def run(json: JValue) = json \ field
}

case class ArrayFieldSelection(field: String, indice: Int) extends JsonPathOperation {
  def run(json: JValue) = (json \ field)(indice)
}