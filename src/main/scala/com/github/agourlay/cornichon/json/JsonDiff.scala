package com.github.agourlay.cornichon.json

import io.circe.{ Json, JsonObject }

// Mostly a port from JSON4s
// https://github.com/json4s/json4s/blob/3.4/ast/src/main/scala/org/json4s/Diff.scala
object JsonDiff {

  case class Diff(changed: Json, added: Json, deleted: Json) {
    def toField(name: String): Diff = {
      def applyTo(x: Json): Json = if (x == Json.Null) Json.Null else Json.fromJsonObject(JsonObject.singleton(name, x))

      Diff(applyTo(changed), applyTo(added), applyTo(deleted))
    }
  }

  private def append(v1: Json, v2: Json): Json =
    if (v1.isNull) v2
    else if (v2.isNull) v1
    else if (v1.isArray && v2.isArray) Json.fromValues(v1.asArray.get ::: v2.asArray.get)
    else if (v1.isArray) Json.fromValues(v1.asArray.get :+ v2)
    else if (v2.isArray) Json.fromValues(v1 +: v2.asArray.get)
    else Json.fromValues(v1 :: v2 :: Nil)

  type JField = (String, Json)

  private def merge(v1: Json, v2: Json): Json = {
    if (v2.isNull) v1
    else v1.deepMerge(v2)
  }

  private def diffJsonObject(v1: JsonObject, v2: JsonObject): Diff = {
    def diffRec(leftFields: List[JField], rightFields: List[JField]): Diff = leftFields match {
      case Nil ⇒ Diff(Json.Null, if (rightFields.isEmpty) Json.Null else Json.fromJsonObject(JsonObject.fromIterable(rightFields)), Json.Null)
      case x :: xs ⇒ rightFields find (_._1 == x._1) match {
        case Some(fieldInBoth) ⇒
          val Diff(c1, a1, d1) = diff(x._2, fieldInBoth._2).toField(fieldInBoth._1)
          val fieldsAdded = rightFields filterNot (_ == fieldInBoth)
          val Diff(c2, a2, d2) = diffRec(xs, fieldsAdded)
          Diff(merge(c1, c2), merge(a1, a2), merge(d1, d2))
        case None ⇒
          val Diff(c, a, d) = diffRec(xs, rightFields)
          Diff(c, a, merge(Json.fromJsonObject(JsonObject.fromIterable(x :: Nil)), d))
      }
    }

    diffRec(v1.toList, v2.toList)
  }

  private def diffJsonArray(v1: List[Json], v2: List[Json]): Diff = {
    def diffRec(xleft: List[Json], yleft: List[Json]): Diff = (xleft, yleft) match {
      case (xs, Nil) ⇒ Diff(Json.Null, Json.Null, if (xs.isEmpty) Json.Null else Json.fromValues(xs))
      case (Nil, ys) ⇒ Diff(Json.Null, if (ys.isEmpty) Json.Null else Json.fromValues(ys), Json.Null)
      case (x :: xs, y :: ys) ⇒
        val Diff(c1, a1, d1) = diff(x, y)
        val Diff(c2, a2, d2) = diffRec(xs, ys)
        Diff(append(c1, c2), append(a1, a2), append(d1, d2))
    }
    diffRec(v1, v2)
  }

  def diff(v1: Json, v2: Json): Diff =
    if (v1 == v2) Diff(Json.Null, Json.Null, Json.Null)
    else if (v1.isObject && v2.isObject) diffJsonObject(v1.asObject.get, v2.asObject.get)
    else if (v1.isArray && v2.isArray) diffJsonArray(v1.asArray.get, v2.asArray.get)
    else if (v1.isNumber && v2.isNumber) Diff(v2, Json.Null, Json.Null)
    else if (v1.isString && v2.isString) Diff(v2, Json.Null, Json.Null)
    else if (v1.isBoolean && v2.isBoolean) Diff(v2, Json.Null, Json.Null)
    else if (v1.isNull) Diff(Json.Null, v2, Json.Null)
    else if (v2.isNull) Diff(Json.Null, Json.Null, v1)
    else Diff(v2, Json.Null, Json.Null)

}
