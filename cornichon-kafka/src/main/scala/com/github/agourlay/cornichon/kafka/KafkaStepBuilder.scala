package com.github.agourlay.cornichon.kafka

import com.github.agourlay.cornichon.core.SessionKey
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.json.JsonPath
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder
import com.github.agourlay.cornichon.matchers.MatcherResolver
import com.github.agourlay.cornichon.resolver.PlaceholderResolver
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }
import cats.instances.string._
import cats.instances.either._

case class KafkaStepBuilder(
    sessionKey: String,
    private val placeholderResolver: PlaceholderResolver,
    private val matcherResolver: MatcherResolver
) {
  def topic_is(expected: String) = AssertStep(
    title = s"topic is $expected",
    action = s ⇒ Assertion.either {
      for {
        actualTopic ← s.getJsonStringField(s"$sessionKey-topic")
        resolvedExpectedTopic ← placeholderResolver.fillPlaceholders(expected)(s)
      } yield GenericEqualityAssertion(resolvedExpectedTopic, actualTopic)
    }
  )

  def message_value = JsonStepBuilder(placeholderResolver, matcherResolver, SessionKey(s"$sessionKey-value"), Some("kafka message value"))

  def key_is(expected: String) = AssertStep(
    title = s"key is $expected",
    action = s ⇒ Assertion.either {
      for {
        actualKey ← s.getJsonStringField(s"$sessionKey-key")
        resolvedExpectedKey ← placeholderResolver.fillPlaceholders(expected)(s)
      } yield GenericEqualityAssertion(resolvedExpectedKey, actualKey)
    }
  )
}
