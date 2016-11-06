package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.CornichonFeature
import scala.concurrent.duration._

class MockServerExample extends CornichonFeature {

  def HttpMock = HttpListenTo(interface = None, portRange = None)_

  lazy val feature = Feature("Cornichon feature mock server examples") {

    Scenario("assert correct number of received calls") {
      HttpMock("awesome-server") {
        Repeat(10) {
          When I get("<awesome-server-url>/")
        }
      }
      And assert httpListen("awesome-server").received_calls(10)
    }

    Scenario("keep valid counters under concurrent requests") {
      HttpMock("awesome-server") {
        Concurrently(2, 10.seconds) {
          Repeat(10) {
            When I get("<awesome-server-url>/")
          }
        }
      }
      And assert httpListen("awesome-server").received_calls(20)
    }

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

      And I show_session
    }

    Scenario("reset registered requests") {
      HttpMock("awesome-server") {
        When I get("<awesome-server-url>/")
        When I get("<awesome-server-url>/requests-received")
        Then assert body.asArray.hasSize(1)

        When I get("<awesome-server-url>/reset")
        When I get("<awesome-server-url>/requests-received")
        Then assert body.asArray.hasSize(0)

        When I get("<awesome-server-url>/")
        When I get("<awesome-server-url>/requests-received")
        Then assert body.asArray.hasSize(1)
      }
    }

    Scenario("toggle error mode") {
      HttpMock("awesome-server") {
        When I get("<awesome-server-url>/")
        Then assert status.is(200)

        When I post("<awesome-server-url>/toggle-error-mode")
        When I get("<awesome-server-url>/")
        Then assert status.is(500)

        When I post("<awesome-server-url>/toggle-error-mode")
        When I get("<awesome-server-url>/")
        Then assert status.is(200)
      }
    }

    Scenario("httpListen blocks can be nested in one another") {
      HttpMock("first-server") {
        HttpMock("second-server") {
          HttpMock("third-server") {
            When I get("<first-server-url>/")
            When I get("<second-server-url>/")
            When I get("<third-server-url>/")
          }
        }
      }
      And assert httpListen("first-server").received_calls(1)
      And assert httpListen("second-server").received_calls(1)
      And assert httpListen("third-server").received_calls(1)
    }

  }

}
