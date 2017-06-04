package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.json.CornichonJson._
import io.circe.{ ACursor, Json }
import cats.instances.string._
import cats.syntax.either._

case class JsonPath(operations: List[JsonPathOperation] = List.empty) {

  val pretty = operations.foldLeft(JsonPath.root)((acc, op) ⇒ s"$acc.${op.pretty}")

  val isRoot = operations.isEmpty

  def run(superSet: Json): Json = cursor(superSet).focus.getOrElse(Json.Null)
  def run(json: String): Either[CornichonError, Json] = parseJson(json).map(run)

  def cursor(input: Json): ACursor = operations.foldLeft[ACursor](input.hcursor) { (oc, op) ⇒
    op match {
      case RootSelection                      ⇒ oc
      case FieldSelection(field)              ⇒ oc.downField(field)
      case RootArrayElementSelection(indice)  ⇒ oc.downArray.rightN(indice)
      case ArrayFieldSelection(field, indice) ⇒ oc.downField(field).downArray.rightN(indice)
    }
  }

  def removeFromJson(input: Json): Json =
    cursor(input).delete.top.getOrElse(input)

}

object JsonPath {
  val root = "$"
  val emptyJsonPath = JsonPath()
  private val rightEmptyJsonPath = Right(emptyJsonPath)

  def parse(path: String) = {
    if (path == root) rightEmptyJsonPath
    else {
      val segments = JsonPathParser.parseJsonPath(path)
      segments.map(fromSegments)
    }
  }

  def parseUnsafe(path: String) =
    parse(path).fold(e ⇒ throw e.toException, identity)

  def run(path: String, json: Json) = JsonPath.parse(path).map(_.run(json))

  def run(path: String, json: String) =
    for {
      json ← parseJson(json)
      jsonPath ← JsonPath.parse(path)
    } yield jsonPath.run(json)

  def fromSegments(segments: List[JsonSegment]) = {
    val operations = segments.map {
      case JsonSegment(JsonPath.root, None)        ⇒ RootSelection
      case JsonSegment(JsonPath.root, Some(index)) ⇒ RootArrayElementSelection(index)
      case JsonSegment(field, None)                ⇒ FieldSelection(field)
      case JsonSegment(field, Some(index))         ⇒ ArrayFieldSelection(field, index)
    }
    JsonPath(operations)
  }

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