---
layout: docs
title:  "Kafka integration"
---

# Kafka integration

`cornichon-kafka` offers a support for [Kafka](https://kafka.apache.org) v2.0.0

The kafka client is shared at the feature level by all the scenarios and is configured with a fixed group-id to 'cornichon-groupId' and is set with offset-reset to 'earliest'.

Due to the architecture of kafka and the handling of consumers offsets, **the default execution of scenarios is sequential**.

- Comprehensive example

```scala
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.kafka.KafkaDsl

class KafkaExample extends CornichonFeature with KafkaDsl {

  override lazy val kafkaBootstrapServersHost: String = "localhost"
  override lazy val kafkaBootstrapServersPort: Int = 9092

  def feature = Feature("Kafka DSL") {

    Scenario("Can write and read arbitrary Strings to/from topic") {
      Given I put_topic(
        topic = "cornichon",
        key = "success",
        message = "I am a plain string"
      )

      When I read_from_topic(
        topic = "cornichon",
        timeoutMs = 500,
        amount = 1
      )

      Then assert kafka("cornichon").topic_is("cornichon")
      Then assert kafka("cornichon").key_is("success")
      Then assert kafka("cornichon").message_value.is("I am a plain string")
    }
    
    Scenario("Can use cornichon jsonAssertions on the message value") {
      Given I put_topic(
        topic = "cornichon",
        key = "json",
        message = """{ "coffee": "black", "cornichon": "green"}"""
      )

      When I read_from_topic("cornichon")
      Then assert kafka("cornichon").topic_is("json")
      Then assert kafka("cornichon").message_value.ignoring("coffee").is("""
        {
          "cornichon": "green"
        }
        """
       )
    }

  }
}
```

Note that this dsl always return the latest `amount` of messages found on the topic.
The consumer polls `timeoutMs` until it does not find any new messages anymore
