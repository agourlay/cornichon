## Kafka Support (experimental)

### Disclaimer

This module is experimental and will be subject to important changes in the future

### Description

Cornichon offers a support for [Kafka](https://kafka.apache.org) v1.0.0

Due to the architecture of kafka and the handling of consumers offsets, **the default execution of
scenarios and features is sequential**.

The underlying kafka client used in cornichon is configured with a fixed group-id to 'cornichon' and is set with offset-reset to 'earliest'.

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
