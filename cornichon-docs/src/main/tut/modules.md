---
layout: docs
title:  "Modules"
position: 6
---

# Modules

The library is composed of several modules with different purposes to enable users to pick and choose according to their needs:

Experimental modules are likely to be subject to important changes in the future.

## Cornichon-scalatest

`cornichon-scalatest` exposes the cornichon features through an integration with `Scalatest`.


## Cornichon-test-framework

`cornichon-test-framework` exposes Cornichon's feature through a direct integration with `SBT test-interface`.

This requires a special configuration in the build.sbt file to use the right `TestFramework`:

`testFrameworks += new TestFramework("com.github.agourlay.cornichon.sbtinterface.CornichonFramework")`

Moreover `CornichonFeature` lives under `com.github.agourlay.cornichon.framework`.


## Http Mock (experimental)

`cornichon-http-mock` contains the `ListenTo` DSL and infrastructure to build tests relying on mocked endpoints.

```
 Scenario("reply to POST request with 201 and assert on received bodies") {
    HttpMock("awesome-server") {
      When I post("<awesome-server-url>/heroes/batman").withBody(
        """
        {
          "name": "Batman",
          "realName": "Bruce Wayne",
          "hasSuperpowers": false
        }
        """
      )

      When I post("<awesome-server-url>/heroes/superman").withBody(
        """
        {
          "name": "Superman",
          "realName": "Clark Kent",
          "hasSuperpowers": true
        }
        """
      )

      Then assert status.is(201)

      // HTTP Mock exposes what it received
      When I get("<awesome-server-url>/requests-received")

      Then assert body.asArray.ignoringEach("headers").is(
        """
        [
          {
            "body" : {
              "name" : "Batman",
              "realName" : "Bruce Wayne",
              "hasSuperpowers" : false
            },
            "url" : "/heroes/batman",
            "method" : "POST",
            "parameters" : {}
          },
          {
            "body" : {
              "name" : "Superman",
              "realName" : "Clark Kent",
              "hasSuperpowers" : true
            },
            "url" : "/heroes/superman",
            "method" : "POST",
            "parameters" : {}
          }
        ]
      """
      )

    }

    // Once HTTP Mock closed, the recorded requests are dumped in the session
    And assert httpListen("awesome-server").received_calls(2)

    And assert httpListen("awesome-server").received_requests.asArray.ignoringEach("headers").is(
      """
        [
          {
            "body" : {
              "name" : "Batman",
              "realName" : "Bruce Wayne",
              "hasSuperpowers" : false
            },
            "url" : "/heroes/batman",
            "method" : "POST",
            "parameters" : {}
          },
          {
            "body" : {
              "name" : "Superman",
              "realName" : "Clark Kent",
              "hasSuperpowers" : true
            },
            "url" : "/heroes/superman",
            "method" : "POST",
            "parameters" : {}
          }
        ]
      """
    )

    And assert httpListen("awesome-server").received_requests.path("$[0].body.name").is("Batman")

    And assert httpListen("awesome-server").received_requests.path("$[1].body").is(
      """
      {
        "name": "Superman",
        "realName": "Clark Kent",
        "hasSuperpowers": true
      }
      """
    )
 }
```


## Kafka Support (experimental)

### Description

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
