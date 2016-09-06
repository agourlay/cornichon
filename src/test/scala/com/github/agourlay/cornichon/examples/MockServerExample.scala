package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.CornichonFeature
import scala.concurrent.duration._

class MockServerExample extends CornichonFeature {

  lazy val feature = Feature("Cornichon feature mock server examples") {

    Scenario("Assert number of received calls") {
      HttpListenTo(label = "awesome-server", port = 9090) {
        Repeat(10) {
          When I get("http://localhost:9090/")
        }
      }
      And assert httpListen("awesome-server").received_calls(10)
    }

    Scenario("Counters valid under concurrent requests") {
      HttpListenTo(label = "awesome-server", port = 9091) {
        Concurrently(2, 10.seconds) {
          Repeat(10) {
            When I get("http://localhost:9091/")
          }
        }
      }
      And assert httpListen("awesome-server").received_calls(20)
    }

    Scenario("Reply to POST request with 201 and assert on received bodies") {
      HttpListenTo(label = "awesome-server", port = 9092) {
        When I post("http://localhost:9092/").withBody(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "hasSuperpowers": false
          }
          """
        )

        When I post("http://localhost:9092/").withBody(
          """
          {
            "name": "Superman",
            "realName": "Clark Kent",
            "hasSuperpowers": true
          }
          """
        )

        Then assert status.is(201)

      }

      And assert httpListen("awesome-server").received_calls(2)

      And assert httpListen("awesome-server").first_received_body.is(
        """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "hasSuperpowers": false
          }
        """
      )

      And assert httpListen("awesome-server").first_received_body.path("name").is("Batman")

      And assert httpListen("awesome-server").received_body_nb(2).is(
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

    Scenario("httpListen blocks can be nested in one another") {
      HttpListenTo(label = "first-server", port = 9093) {
        HttpListenTo(label = "second-server", port = 9094) {
          HttpListenTo(label = "third-server", port = 9095) {
            When I get("http://localhost:9093/")
            When I get("http://localhost:9094/")
            When I get("http://localhost:9095/")
          }
        }
      }
      And assert httpListen("first-server").received_calls(1)
      And assert httpListen("second-server").received_calls(1)
      And assert httpListen("third-server").received_calls(1)

    }

  }

}
