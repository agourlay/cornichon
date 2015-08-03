package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.server.RestAPI
import org.scalatest.{ WordSpec, BeforeAndAfterAll }

import scala.concurrent.duration._

import scala.concurrent.Await

trait ExampleServer extends BeforeAndAfterAll {
  self: WordSpec ⇒

  val port = 8080
  val server = Await.result(new RestAPI().start(port), 5 second)

  override def afterAll() = {
    server.unbind()
  }
}
