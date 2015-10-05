package com.github.agourlay.cornichon.http

import org.scalatest.{ WordSpec, Matchers }
import scala.concurrent.duration._

class HttpServiceSpec extends WordSpec with Matchers {

  val service = new HttpService("", 2000 millis)

  "HttpService" must {
    "fill in session" in {

    }
  }
}
