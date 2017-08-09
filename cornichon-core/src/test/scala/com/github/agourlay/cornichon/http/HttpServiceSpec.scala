package com.github.agourlay.cornichon.http

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.scalatest.{ EitherMatchers, EitherValues }
import com.github.agourlay.cornichon.core.Session
import com.github.agourlay.cornichon.http.client.AkkaHttpClient
import com.github.agourlay.cornichon.resolver.Resolver
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class HttpServiceSpec extends WordSpec
  with Matchers
  with BeforeAndAfterAll
  with EitherValues
  with EitherMatchers {

  implicit val system = ActorSystem("akka-http-client")
  implicit val mat = ActorMaterializer()

  val client = new AkkaHttpClient()
  val service = new HttpService("", 2000 millis, client, Resolver.withoutExtractor())

  override def afterAll() = {
    client.shutdown().map { _ ⇒ system.terminate() }
  }

  "HttpService" when {
    "fillInSessionWithResponse" must {
      "extract content with NoOpExtraction" in {
        val resp = CornichonHttpResponse(200, Nil, "hello world")
        val filledSession = service.fillInSessionWithResponse(Session.newEmpty, resp, NoOpExtraction)
        filledSession.value.get("last-response-status") should beRight("200")
        filledSession.value.get("last-response-body") should beRight("hello world")
      }

      "extract content with RootResponseExtraction" in {
        val resp = CornichonHttpResponse(200, Nil, "hello world")
        val filledSession = service.fillInSessionWithResponse(Session.newEmpty, resp, RootExtractor("copy-body"))
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
        val filledSession = service.fillInSessionWithResponse(Session.newEmpty, resp, PathExtractor("name", "part-of-body"))
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

    "resolveParams" must {
      "resolve also params in URL" in {
        val s = Session.newEmpty
          .addValues("hero" → "batman", "color" → "blue")
        val url = "http://yada.com?hero=<hero>&color=<color>"
        service.resolveParams(url, params = Seq.empty)(s) should beRight(Seq("hero" → "batman", "color" → "blue"))
      }

      "detect non resolvable params" in {
        val s = Session.newEmpty
          .addValues("hero" → "batman", "color" → "blue")
        val url = "http://yada.com?hero=<hero>&color=<color2>"
        service.resolveParams(url, params = Seq.empty)(s).isLeft should be(true)
      }

      "handle URL without param" in {
        val url = "http://yada.com"
        service.resolveParams(url, params = Seq.empty)(Session.newEmpty) should beRight(Seq.empty[(String, String)])
      }
    }

    "decodeSessionHeaders" must {
      "fail if wrong format" in {
        HttpService.decodeSessionHeaders("headerkey-headervalue") should be(left)
      }
    }
  }
}
