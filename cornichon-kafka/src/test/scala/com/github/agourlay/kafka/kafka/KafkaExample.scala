package com.github.agourlay.kafka.kafka

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.kafka.KafkaDsl

class KafkaExample extends CornichonFeature with KafkaDsl {

  def feature = Feature("Kafka test") {

    Scenario("put to topic") {

      Given I put_topic("my-topic", "my-key", "my-json")

      And I show_session

    }

  }
}
