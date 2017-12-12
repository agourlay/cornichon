---
layout: docs
title:  "Modules"
position: 6
---

# Modules

The library is composed of several modules with different purposes to enable users to pick and choose according to their needs:

Experimental modules are likely to be subject to important changes in the future.

## Cornichon

```cornichon``` exposes the cornichon features through an integration with ```Scalatest```. (might be renamed to ```cornichon-scalatest``` later)


## Cornichon without Scalatest (experimental)

```cornichon-experimental``` exposes Cornichon's feature through a direct integration with ```SBT test-interface```.

This requires a special configuration in the build.sbt file to use the right `TestFramework`:

`testFrameworks += new TestFramework("com.github.agourlay.cornichon.experimental.sbtinterface.CornichonFramework")`

Moreover `CornichonFeature` lives under `com.github.agourlay.cornichon.experimental`.


## Http Mock (experimental)

```cornichon-http-mock``` contains the ```ListenTo``` DSL and infrastructure to build tests relying on mocked endpoints.

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

```cornichon-kafka``` offers a support for [Kafka](https://kafka.apache.org) v1.0.0

Due to the architecture of kafka and the handling of consumers offsets, **the default execution of
scenarios and features is sequential**.

The underlying kafka client used in cornichon is configured with a fixed group-id to 'cornichon' and is set with offset-reset to 'earliest'.

- Configuration of the client

Put to the application.conf

```
kafka {
  bootstrapServers = "localhost:9092"
}
```

- putting a message to a topic

```

Given I put_topic(topic = "my-topic", key = "my-key", message = "the actual message")

```

- getting a message from a topic

```

Then I read_from_topic(topic = "my-topic", amount = 1, timeout = 1000)

Then assert session_value("my-topic").asJson.ignoring("timestamp").is(
"""
    {
     "key": "my-key",
     "topic": "my-topic",
     "value": "the actual message"
    }
""")


```

Note that this dsl always return the latest `amount` of messages found on the topic.
The consumer polls `timeout` ms until it does not find any new messages anymore

It is also possible to use a different session key to store the messages from the topic, then the topic-name itself

```

Then I read_from_topic(topic = "my-topic", amount = 1, timeout = 1000, targetKey = Some("message"))

Then assert session_value("message").asJson.ignoring("timestamp").is(
"""
    {
     "key": "my-key",
     "topic": "my-topic",
     "value": "the actual message"
    }
""")


```

Most of the time, the message on the topic is json-formatted. In order to use the convenient JsonMatchers of cornichon,
the message can be read as json:

```
Given I put_topic(topic = "my-topic", key = "my-key", message =
"""
    {
       "cornichon": "mon dieu",
       "cucumber": "sacre bleu"
    }
""")
Then I read_json_from_topic(topic = "my-topic", amount = 1, timeout = 1000, targetKey = Some("message"))
Then assert session_value("message").asJson.ignoring("cucumber").is(
"""
   {
       "cornichon": "mon dieu"
   }
"""
)
```