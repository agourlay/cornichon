package com.github.agourlay.kafka.kafka

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.kafka.KafkaDsl

class KafkaExample extends CornichonFeature with KafkaDsl {

  def feature = Feature("Kafka test") {

    Scenario("put to topic") {

      Given I put_topic("my-topic", "my-key", "my-json")

      And I show_session

      Then I read_from_topic("my-topic", amount = 1)

      Then assert session_value("my-topic").is("""{my-json}""")

      Then assert session_value("my-topic").asJson.is(
        """
          {
             "key": "my-key",
             "topic": "my-topic",
             "value": "my-json"
          }
        """)

    }

  }
}
