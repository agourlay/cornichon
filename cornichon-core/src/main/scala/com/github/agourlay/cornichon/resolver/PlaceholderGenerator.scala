package com.github.agourlay.cornichon.resolver

import java.util.UUID

import com.github.agourlay.cornichon.core.RandomContext

case class PlaceholderGenerator(key: String, gen: RandomContext => String)

object PlaceholderGenerator {
  // RandomContext
  val randomUUID = PlaceholderGenerator("random-uuid", rc => new UUID(rc.nextLong(), rc.nextLong()).toString)
  val randomPositiveInteger = PlaceholderGenerator("random-positive-integer", _.nextInt(10000).toString)
  val randomString = PlaceholderGenerator("random-string", _.nextString(5))
  val randomAlphanumString = PlaceholderGenerator("random-alphanum-string", _.alphanumeric(5))
  val randomBoolean = PlaceholderGenerator("random-boolean", _.nextBoolean().toString)
  val scenarioUniqueNumber = PlaceholderGenerator("scenario-unique-number", _.uniqueLong().toString)

  //Global
  val globalUniqueNumber = PlaceholderGenerator("global-unique-number", _ => PlaceholderResolver.globalNextLong().toString)
  val randomTimestamp = PlaceholderGenerator("random-timestamp", rc => (Math.abs(System.currentTimeMillis - rc.nextLong()) / 1000).toString)
  val currentTimestamp = PlaceholderGenerator("current-timestamp", _ => (System.currentTimeMillis / 1000).toString)
}
