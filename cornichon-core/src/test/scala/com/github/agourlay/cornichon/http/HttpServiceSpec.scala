package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.core.Session
import utest._

object HttpServiceSpec extends TestSuite {

  val tests = Tests {
    test("fillInSessionWithResponse extracts content with NoOpExtraction") {
      val resp = CornichonHttpResponse(200, Nil, "hello world")
      val filledSession = HttpService.fillInSessionWithResponse(Session.newEmpty, NoOpExtraction)(resp)
      assert(filledSession.flatMap(_.get("last-response-status")) == Right("200"))
      assert(filledSession.flatMap(_.get("last-response-body")) == Right("hello world"))
    }

    test("fillInSessionWithResponse extracts content with RootResponseExtraction") {
      val resp = CornichonHttpResponse(200, Nil, "hello world")
      val filledSession = HttpService.fillInSessionWithResponse(Session.newEmpty, RootExtractor("copy-body"))(resp)
      assert(filledSession.flatMap(_.get("last-response-status")) == Right("200"))
      assert(filledSession.flatMap(_.get("last-response-body")) == Right("hello world"))
      assert(filledSession.flatMap(_.get("copy-body")) == Right("hello world"))
    }

    test("fillInSessionWithResponse extracts content with PathResponseExtraction") {
      val resp = CornichonHttpResponse(200, Nil, """{ "name" : "batman" }""")
      val filledSession = HttpService.fillInSessionWithResponse(Session.newEmpty, PathExtractor("name", "part-of-body"))(resp)
      assert(filledSession.flatMap(_.get("last-response-status")) == Right("200"))
      assert(filledSession.flatMap(_.get("last-response-body")) == Right("""{ "name" : "batman" }"""))
      assert(filledSession.flatMap(_.get("part-of-body")) == Right("batman"))
    }

    test("decodeSessionHeaders fail a if wrong format") {
      assert(HttpService.decodeSessionHeaders("headerkey-headervalue").isLeft)
    }
  }
}
