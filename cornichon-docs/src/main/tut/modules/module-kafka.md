---
layout: docs
title:  "Kafka support"
---

# Kafka support

`cornichon-kafka` offers a support for [Kafka](https://kafka.apache.org) v1.0.0

Due to the architecture of kafka and the handling of consumers offsets, **the default execution of
scenarios and features is sequential**.

The underlying kafka client used in cornichon is configured with a fixed group-id to 'cornichon' and is set with offset-reset to 'earliest'.

- Comprehensive example

```scala
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.kafka.KafkaDsl

class KafkaExample extends CornichonFeature with KafkaDsl {

  def feature = Feature("Kafka DSL") {

    Scenario("Can write and read arbitrary Strings to/from topic") {
      Given I put_topic(
        topic = "cornichon",
        key = "success",
        message = "I am a plain string"
      )

      When I read_from_topic(
        topic = "cornichon",
        timeout = 500,
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
}


```

Note that this dsl always return the latest `amount` of messages found on the topic.
The consumer polls `timeout` ms until it does not find any new messages anymore
