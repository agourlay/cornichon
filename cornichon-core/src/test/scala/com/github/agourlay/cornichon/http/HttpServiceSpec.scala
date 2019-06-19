package com.github.agourlay.cornichon.http

import cats.scalatest.{ EitherMatchers, EitherValues }
import cats.instances.string._

import com.github.agourlay.cornichon.core.{ Config, Session }
import com.github.agourlay.cornichon.http.HttpMethods.GET
import com.github.agourlay.cornichon.http.client.NoOpHttpClient
import com.github.agourlay.cornichon.resolver.PlaceholderResolver

import monix.execution.Scheduler.Implicits.global

import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }

import scala.concurrent.duration._

class HttpServiceSpec extends WordSpec
  with Matchers
  with BeforeAndAfterAll
  with EitherValues
  with EitherMatchers {

  val client = new NoOpHttpClient
  val service = new HttpService("", 2000.millis, client, PlaceholderResolver.default(), Config())
  val dummyRequest = HttpRequest[String](GET, "", None, Nil, Nil)

  "HttpService" when {
    "fillInSessionWithResponse" must {
      "extract content with NoOpExtraction" in {
        val resp = CornichonHttpResponse(200, Nil, "hello world")
        val filledSession = HttpService.fillInSessionWithResponse(Session.newEmpty, NoOpExtraction)(resp)
        filledSession.value.get("last-response-status") should beRight("200")
        filledSession.value.get("last-response-body") should beRight("hello world")
      }

      "extract content with RootResponseExtraction" in {
        val resp = CornichonHttpResponse(200, Nil, "hello world")
        val filledSession = HttpService.fillInSessionWithResponse(Session.newEmpty, RootExtractor("copy-body"))(resp)
        filledSession.value.get("last-response-status") should beRight("200")
        filledSession.value.get("last-response-body") should beRight("hello world")
        filledSession.value.get("copy-body") should beRight("hello world")
      }

      "extract content with PathResponseExtraction" in {
        val resp = CornichonHttpResponse(200, Nil,
          """
            {
              "name" : "batman"
            }
          """)
        val filledSession = HttpService.fillInSessionWithResponse(Session.newEmpty, PathExtractor("name", "part-of-body"))(resp)
        filledSession.value.get("last-response-status") should beRight("200")
        filledSession.value.get("last-response-body") should beRight(
          """
            {
              "name" : "batman"
            }
          """
        )
        filledSession.value.get("part-of-body") should beRight("batman")
      }
    }

    "decodeSessionHeaders" must {
      "fail if wrong format" in {
        HttpService.decodeSessionHeaders("headerkey-headervalue") should be(left)
      }
    }

    "configureRequest" must {
      "add accept gzip according to config - true" in {
        val config = Config(addAcceptGzipByDefault = true)
        HttpService.configureRequest(dummyRequest, config).headers should be(("Accept-Encoding" -> "gzip") :: Nil)
      }

      "add accept gzip according to config - false" in {
        val config = Config(addAcceptGzipByDefault = false)
        HttpService.configureRequest(dummyRequest, config).headers should be(Nil)
      }

    }
  }
}
