---
layout: docs
title:  "HTTP mock"
---

# HTTP Mock

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