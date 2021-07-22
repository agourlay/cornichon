package com.github.agourlay.cornichon.util

import io.circe.{ Encoder, Json, JsonObject }

import scala.concurrent.duration.FiniteDuration

object CirceUtil {
  // taken from https://github.com/circe/circe/pull/978
  implicit final val finiteDurationEncoder: Encoder[FiniteDuration] = new Encoder[FiniteDuration] {
    final def apply(a: FiniteDuration): Json =
      Json.fromJsonObject(
        JsonObject(
          "length" -> Json.fromLong(a.length),
          "unit" -> Json.fromString(a.unit.name)))
  }
}
