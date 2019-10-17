package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.core.Session
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }

class HttpServiceSpec extends WordSpec
  with Matchers
  with BeforeAndAfterAll {

  "HttpService" when {
    "fillInSessionWithResponse" must {
      "extract content with NoOpExtraction" in {
        val resp = CornichonHttpResponse(200, Nil, "hello world")
        val filledSession = HttpService.fillInSessionWithResponse(Session.newEmpty, NoOpExtraction)(resp)
        filledSession.flatMap(_.get("last-response-status")) should be(Right("200"))
        filledSession.flatMap(_.get("last-response-body")) should be(Right("hello world"))
      }

      "extract content with RootResponseExtraction" in {
        val resp = CornichonHttpResponse(200, Nil, "hello world")
        val filledSession = HttpService.fillInSessionWithResponse(Session.newEmpty, RootExtractor("copy-body"))(resp)
        filledSession.flatMap(_.get("last-response-status")) should be(Right("200"))
        filledSession.flatMap(_.get("last-response-body")) should be(Right("hello world"))
        filledSession.flatMap(_.get("copy-body")) should be(Right("hello world"))
      }

      "extract content with PathResponseExtraction" in {
        val resp = CornichonHttpResponse(200, Nil,
          """
            {
              "name" : "batman"
            }
          """)
        val filledSession = HttpService.fillInSessionWithResponse(Session.newEmpty, PathExtractor("name", "part-of-body"))(resp)
        filledSession.flatMap(_.get("last-response-status")) should be(Right("200"))
        filledSession.flatMap(_.get("last-response-body")) should be(Right(
          """
            {
              "name" : "batman"
            }
          """
        ))
        filledSession.flatMap(_.get("part-of-body")) should be(Right("batman"))
      }
    }

    "decodeSessionHeaders" must {
      "fail if wrong format" in {
        HttpService.decodeSessionHeaders("headerkey-headervalue").isLeft should be(true)
      }
    }
  }
}
