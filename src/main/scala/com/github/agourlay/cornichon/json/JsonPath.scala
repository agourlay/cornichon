package com.github.agourlay.cornichon.json

import org.json4s.JsonAST.JValue

import scala.language.dynamics

case class JsonPath(operations: List[JsonPathOperation] = List.empty) extends Dynamic {
  def selectDynamic(field: String): JsonPath = this.copy(operations = operations :+ FieldSelection(field))

  def applyDynamic(name: String)(args: Int*) = {
    val index = args.head
    this.copy(operations = operations :+ ArrayFieldSelection(name, index))
  }

  val pretty = operations.foldLeft("$")((acc, op) ⇒ s"$acc.${op.pretty}")

  val isRoot = operations.isEmpty

  def run(json: JValue) = operations.foldLeft(json) { (j, op) ⇒ op.run(j) }
}

object JsonPath {
  val root = JsonPath()

  def fromSegments(segments: List[JsonSegment]) = {
    val operations = segments.map {
      case JsonSegment(field, None)        ⇒ FieldSelection(field)
      case JsonSegment(field, Some(index)) ⇒ ArrayFieldSelection(field, index)
    }
    JsonPath(operations)
  }

  implicit def fromString(field: String): JsonPath = {
    val segments = JsonPathParser.parseJsonPath(field)
    fromSegments(segments)
  }

  implicit def fromTupleString(tuple: (String, String)): (JsonPath, String) = {
    (fromSegments(JsonPathParser.parseJsonPath(tuple._1)), tuple._2)
  }

  implicit def fromStrings(fields: Seq[(String, String)]): Seq[(JsonPath, String)] = fields.map(fromTupleString)
}

sealed trait JsonPathOperation {
  val field: String
  val pretty: String
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