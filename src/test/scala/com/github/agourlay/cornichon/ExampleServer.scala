package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.server.RestAPI
import org.scalatest.{ BeforeAndAfterAll, WordSpec }

import scala.concurrent.Await
import scala.concurrent.duration._

trait ExampleServer extends BeforeAndAfterAll {
  self: WordSpec ⇒

  val port = 8080
  val server = Await.result(new RestAPI().start(port), 5 second)

  override def afterAll() = {
    server.unbind()
  }
}
