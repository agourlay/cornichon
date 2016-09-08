package com.github.agourlay.cornichon.json

import io.circe.{ Json, JsonObject }

// Mostly a port from JSON4s
// https://github.com/json4s/json4s/blob/3.4/ast/src/main/scala/org/json4s/Diff.scala
object JsonDiffer {

  case class JsonDiff(changed: Json, added: Json, deleted: Json) {
    def asField(name: String): JsonDiff = {
      def bindTo(x: Json): Json = if (x == Json.Null) Json.Null else Json.fromJsonObject(JsonObject.singleton(name, x))
      JsonDiff(bindTo(changed), bindTo(added), bindTo(deleted))
    }
  }

  private def append(v1: Json, v2: Json): Json =
    if (v1.isNull) v2
    else if (v2.isNull) v1
    else if (v1.isArray && v2.isArray) Json.fromValues(v1.asArray.get ::: v2.asArray.get)
    else if (v1.isArray) Json.fromValues(v1.asArray.get :+ v2)
    else if (v2.isArray) Json.fromValues(v1 +: v2.asArray.get)
    else Json.fromValues(v1 :: v2 :: Nil)

  private def merge(v1: Json, v2: Json) = if (v2.isNull) v1 else v1.deepMerge(v2)

  private def diffJsonObject(v1: JsonObject, v2: JsonObject): JsonDiff = {
    def diffRec(leftFields: List[(String, Json)], rightFields: List[(String, Json)]): JsonDiff = leftFields match {
      case Nil ⇒ JsonDiff(Json.Null, if (rightFields.isEmpty) Json.Null else Json.fromJsonObject(JsonObject.fromIterable(rightFields)), Json.Null)
      case x :: xs ⇒ rightFields find (_._1 == x._1) match {
        case Some(fieldInBoth) ⇒
          val JsonDiff(c1, a1, d1) = diff(x._2, fieldInBoth._2).asField(fieldInBoth._1)
          val fieldsAdded = rightFields filterNot (_ == fieldInBoth)
          val JsonDiff(c2, a2, d2) = diffRec(xs, fieldsAdded)
          JsonDiff(merge(c1, c2), merge(a1, a2), merge(d1, d2))
        case None ⇒
          val JsonDiff(c, a, d) = diffRec(xs, rightFields)
          JsonDiff(c, a, merge(Json.fromJsonObject(JsonObject.fromIterable(x :: Nil)), d))
      }
    }

    diffRec(v1.toList, v2.toList)
  }

  private def diffJsonArray(v1: List[Json], v2: List[Json]): JsonDiff = {
    def diffRec(xleft: List[Json], yleft: List[Json]): JsonDiff = (xleft, yleft) match {
      case (xs, Nil) ⇒ JsonDiff(Json.Null, Json.Null, if (xs.isEmpty) Json.Null else Json.fromValues(xs))
      case (Nil, ys) ⇒ JsonDiff(Json.Null, if (ys.isEmpty) Json.Null else Json.fromValues(ys), Json.Null)
      case (x :: xs, y :: ys) ⇒
        val JsonDiff(c1, a1, d1) = diff(x, y)
        val JsonDiff(c2, a2, d2) = diffRec(xs, ys)
        JsonDiff(append(c1, c2), append(a1, a2), append(d1, d2))
    }
    diffRec(v1, v2)
  }

  def diff(v1: Json, v2: Json): JsonDiff =
    if (v1 == v2) JsonDiff(Json.Null, Json.Null, Json.Null)
    else if (v1.isObject && v2.isObject) diffJsonObject(v1.asObject.get, v2.asObject.get)
    else if (v1.isArray && v2.isArray) diffJsonArray(v1.asArray.get, v2.asArray.get)
    else if (v1.isNumber && v2.isNumber) JsonDiff(v2, Json.Null, Json.Null)
    else if (v1.isString && v2.isString) JsonDiff(v2, Json.Null, Json.Null)
    else if (v1.isBoolean && v2.isBoolean) JsonDiff(v2, Json.Null, Json.Null)
    else if (v1.isNull) JsonDiff(Json.Null, v2, Json.Null)
    else if (v2.isNull) JsonDiff(Json.Null, Json.Null, v1)
    else JsonDiff(v2, Json.Null, Json.Null)

}
