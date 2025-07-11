package com.github.agourlay.cornichon.framework.examples.propertyCheck.stringReverse

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core.{ Generator, RandomContext, ValueGenerator }
import com.github.agourlay.cornichon.framework.examples.HttpServer

import scala.concurrent.Await
import scala.concurrent.duration._

class StringReverseCheck extends CornichonFeature {

  def feature = Feature("String reverse for_all checks") {

    Scenario("reverse a string twice yields the same results") {

      Given check for_all("reversing twice a string yields the same result", maxNumberOfRuns = 5, stringGen) { randomString =>
        Attach {
          Given I post("/double-reverse").withParams("word" -> randomString)
          Then assert status.is(200)
          Then assert body.is(randomString)
        }
      }
    }
  }

  def stringGen(rc: RandomContext): Generator[String] = ValueGenerator(
    name = "alphanumeric String (20)",
    gen = () => rc.alphanumeric(20))

  lazy val port = 8080

  // Base url used for all HTTP steps
  override lazy val baseUrl = s"http://localhost:$port"

  //Travis CI struggles with default value `2.seconds`
  override lazy val requestTimeout = 5.second

  var server: HttpServer = _

  // Starts up test server
  beforeFeature {
    server = Await.result(new ReverseAPI().start(port), 5.second)
  }

  // Stops test server
  afterFeature {
    Await.result(server.shutdown(), 5.second)
  }
}
