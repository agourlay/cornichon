package com.github.agourlay.cornichon.kafka

import com.github.agourlay.cornichon.core.SessionKey
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }
import cats.instances.string._

case class KafkaStepBuilder(sessionKey: String) {
  def topic_is(expected: String) = AssertStep(
    title = s"topic is $expected",
    action = sc ⇒ Assertion.either {
      for {
        actualTopic ← sc.session.getJsonStringField(s"$sessionKey-topic")
        resolvedExpectedTopic ← sc.fillPlaceholders(expected)
      } yield GenericEqualityAssertion(resolvedExpectedTopic, actualTopic)
    }
  )

  def message_value = JsonStepBuilder(SessionKey(s"$sessionKey-value"), Some("kafka message value"))

  def key_is(expected: String) = AssertStep(
    title = s"key is $expected",
    action = sc ⇒ Assertion.either {
      for {
        actualKey ← sc.session.getJsonStringField(s"$sessionKey-key")
        resolvedExpectedKey ← sc.fillPlaceholders(expected)
      } yield GenericEqualityAssertion(resolvedExpectedKey, actualKey)
    }
  )
}
