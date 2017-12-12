package com.github.agourlay.kafka.kafka

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.kafka.{ KafkaConfig, KafkaDsl }
import net.manub.embeddedkafka.{ EmbeddedKafka, EmbeddedKafkaConfig }

class KafkaExample extends CornichonFeature with KafkaDsl with KafkaConfig {

  def feature = Feature("Kafka test") {

    Scenario("put and read to topic") {

      Given I put_topic("my-topic", "my-key", "my-json")

      Then I read_from_topic("my-topic", amount = 1, timeout = 1000)

      Then assert session_value("my-topic").asJson.ignoring("timestamp").is(
        """
          {
             "key": "my-key",
             "topic": "my-topic",
             "value": "my-json"
          }
        """)

    }

    Scenario("put and read to topic 2 ") {

      Given I put_topic("my-topic", "my-key", "my-json")

      Then I read_from_topic("my-topic", amount = 1, timeout = 1000)

      Then assert session_value("my-topic").asJson.ignoring("timestamp").is(
        """
          {
             "key": "my-key",
             "topic": "my-topic",
             "value": "my-json"
          }
        """)

    }

    Scenario("put and read to other topic ") {

      Given I put_topic("my-topic-2", "my-key", "my-json")

      Then I read_from_topic("my-topic", amount = 1, timeout = 1000)

      Then assert session_value("my-topic").isAbsent

    }

  }
  override def beforeAll() = {
    EmbeddedKafka.start()(EmbeddedKafkaConfig(
      kafkaPort = 9092,
      customBrokerProperties = Map(
        "group.initial.rebalance.delay.ms" -> "10000"
      )
    ))
  }
  override def afterAll() = {
    EmbeddedKafka.stop()
  }

}
