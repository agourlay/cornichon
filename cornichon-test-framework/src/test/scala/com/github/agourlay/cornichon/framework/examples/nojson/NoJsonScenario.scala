package com.github.agourlay.cornichon.framework.examples.nojson

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.framework.examples.HttpServer

import scala.concurrent.Await
import scala.concurrent.duration._

class NoJsonScenario extends CornichonFeature {

  lazy val octocatFile = getClass.getResource("/Octocat.jpg")

  def feature = Feature("Basic examples handling of non JSON payload") {
    Scenario("post file form data") {
      Given I post_file_form_data("/multipart").withBody(octocatFile)
      Then assert status.is(200)
    }

    Scenario("post form url encoded") {
      Given I post_form_url_encoded("/multipart").withBody(
        List(
          "name" -> "bruce",
          "age" -> "22",
          "city" -> "London"
        )
      )
      Then assert status.is(200)
    }

    Scenario("post form data") {
      Given I post_form_data("/multipart").withBody(
        List(
          "name" -> "bruce",
          "age" -> "22",
          "city" -> "London"
        )
      )
      Then assert status.is(200)
    }

  }

  lazy val port = 8080

  // Base url used for all HTTP steps
  override lazy val baseUrl = s"http://localhost:$port"

  //Travis CI struggles with default value `2.seconds`
  override lazy val requestTimeout = 5.seconds

  var server: HttpServer = _

  // Starts up test server
  beforeFeature {
    server = Await.result(new NoJsonAPI().start(port), 5.seconds)
  }

  // Stops test server
  afterFeature {
    Await.result(server.shutdown(), 5.seconds)
  }
}