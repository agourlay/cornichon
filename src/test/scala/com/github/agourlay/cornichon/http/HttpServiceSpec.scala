package com.github.agourlay.cornichon.http

import akka.http.scaladsl.model.StatusCodes
import com.github.agourlay.cornichon.core.{ Session, Resolver }
import com.github.agourlay.cornichon.http.client.AkkaHttpClient
import org.scalatest.{ WordSpec, Matchers }
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class HttpServiceSpec extends WordSpec with Matchers {

  implicit private val ec: ExecutionContext = ExecutionContext.global
  val service = new HttpService("", 2000 millis, AkkaHttpClient.default, new Resolver, new CornichonJson)

  "HttpService" must {
    "fill in session" in {
      val s = Session.newSession
      val resp = CornichonHttpResponse(StatusCodes.OK, Nil, "hello world")
      service.fillInHttpSession(s, resp)
    }
  }
}
