package com.github.agourlay.kafka.kafka

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.kafka.KafkaDsl
import net.manub.embeddedkafka.{ EmbeddedKafka, EmbeddedKafkaConfig }

class KafkaExample extends CornichonFeature with KafkaDsl {

  def feature = Feature("Kafka DSL") {

    Scenario("write and read arbitrary Strings to/from topic") {
      Given I put_topic(
        topic = "cornichon",
        key = "success",
        message = "I am a plain string"
      )

      When I read_from_topic(
        topic = "cornichon"
      )

      Then assert kafka("cornichon").topic_is("cornichon")
      Then assert kafka("cornichon").key_is("success")
      Then assert kafka("cornichon").message_value.is("I am a plain string")
    }

    Scenario("use cornichon jsonAssertions on the message value") {
      Given I put_topic(
        topic = "cornichon",
        key = "json",
        message =
          """{
            | "coffee"   : "black",
            | "cornichon": "green"
            |}""".stripMargin
      )

      When I read_from_topic(
        topic = "cornichon"
      )

      Then assert kafka("cornichon").message_value.ignoring("coffee").is(
        """{
          | "cornichon": "green"
          |}""".stripMargin)
    }

  }

  beforeFeature {
    implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(
      kafkaPort = 9092,
      customBrokerProperties = Map("group.initial.rebalance.delay.ms" -> "10")
    )
    EmbeddedKafka.start()
    ()
  }

  afterFeature {
    EmbeddedKafka.stop()
  }

}
