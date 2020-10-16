package com.github.agourlay.cornichon.http

import cats.syntax.show._
import com.github.agourlay.cornichon.core.Session
import com.github.agourlay.cornichon.http.HttpMethods.GET
import utest._

object HttpServiceSpec extends TestSuite {

  private val dummyRequest = HttpRequest[String](GET, "http://a/b", Some("a test request body"), Nil, Nil)

  val tests = Tests {
    test("fillInSessionWithResponse extracts content with NoOpExtraction") {
      val resp = HttpResponse(200, Nil, "hello world")
      val filledSession = HttpService.fillInSessionWithResponse(Session.newEmpty, NoOpExtraction, dummyRequest.show)(resp)
      assert(filledSession.flatMap(_.get("last-response-status")) == Right("200"))
      assert(filledSession.flatMap(_.get("last-response-body")) == Right("hello world"))
      assert(filledSession.flatMap(_.get("last-response-request")) == Right(dummyRequest.show))
    }

    test("fillInSessionWithResponse extracts content with RootResponseExtraction") {
      val resp = HttpResponse(200, Nil, "hello world")
      val filledSession = HttpService.fillInSessionWithResponse(Session.newEmpty, RootExtractor("copy-body"), dummyRequest.show)(resp)
      assert(filledSession.flatMap(_.get("last-response-status")) == Right("200"))
      assert(filledSession.flatMap(_.get("last-response-body")) == Right("hello world"))
      assert(filledSession.flatMap(_.get("copy-body")) == Right("hello world"))
    }

    test("fillInSessionWithResponse extracts content with PathResponseExtraction") {
      val resp = HttpResponse(200, Nil, """{ "name" : "batman" }""")
      val filledSession = HttpService.fillInSessionWithResponse(Session.newEmpty, PathExtractor("name", "part-of-body"), dummyRequest.show)(resp)
      assert(filledSession.flatMap(_.get("last-response-status")) == Right("200"))
      assert(filledSession.flatMap(_.get("last-response-body")) == Right("""{ "name" : "batman" }"""))
      assert(filledSession.flatMap(_.get("part-of-body")) == Right("batman"))
    }

    test("decodeSessionHeaders fail a if wrong format") {
      assert(HttpService.decodeSessionHeaders("headerkey-headervalue").isLeft)
    }
  }
}
