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
  implicit val scheduler = system.scheduler
  implicit val mat = ActorMaterializer()

  val client = new AkkaHttpClient()
  val service = new HttpService("", 2000 millis, client, Resolver.withoutExtractor())

  override def afterAll() = {
    client.shutdown().map { _ ⇒ system.terminate() }
  }

  "HttpService" when {
    "fillInSessionWithResponse" must {
      "extract content with NoOpExtraction" in {
        val s = Session.newEmpty
        val resp = CornichonHttpResponse(200, Nil, "hello world")
        val filledSession = service.fillInSessionWithResponse(s, resp, NoOpExtraction)
        filledSession.value.getUnsafe("last-response-status") should be("200")
        filledSession.value.getUnsafe("last-response-body") should be("hello world")
      }

      "extract content with RootResponseExtraction" in {
        val s = Session.newEmpty
        val resp = CornichonHttpResponse(200, Nil, "hello world")
        val filledSession = service.fillInSessionWithResponse(s, resp, RootExtractor("copy-body"))
        filledSession.value.getUnsafe("last-response-status") should be("200")
        filledSession.value.getUnsafe("last-response-body") should be("hello world")
        filledSession.value.getUnsafe("copy-body") should be("hello world")
      }

      "extract content with PathResponseExtraction" in {
        val s = Session.newEmpty
        val resp = CornichonHttpResponse(200, Nil,
          """
            {
              "name" : "batman"
            }
          """)
        val filledSession = service.fillInSessionWithResponse(s, resp, PathExtractor("name", "part-of-body"))
        filledSession.value.getUnsafe("last-response-status") should be("200")
        filledSession.value.getUnsafe("last-response-body") should be(
          """
            {
              "name" : "batman"
            }
          """
        )
        filledSession.value.getUnsafe("part-of-body") should be("batman")
      }
    }

    "resolveParams" must {
      "resolve also params in URL" in {
        val s = Session.newEmpty
          .addValues(Seq("hero" → "batman", "color" → "blue"))
        val url = "http://yada.com?hero=<hero>&color=<color>"
        service.resolveParams(url, params = Seq.empty)(s) should beRight(Seq("hero" → "batman", "color" → "blue"))
      }

      "detect non resolvable params" in {
        val s = Session.newEmpty
          .addValues(Seq("hero" → "batman", "color" → "blue"))
        val url = "http://yada.com?hero=<hero>&color=<color2>"
        service.resolveParams(url, params = Seq.empty)(s).isLeft should be(true)
      }

      "handle URL without param" in {
        val s = Session.newEmpty
        val url = "http://yada.com"
        service.resolveParams(url, params = Seq.empty)(s) should beRight(Seq.empty[(String, String)])
      }
    }

    "decodeSessionHeaders" must {
      "fail if wrong format" in {
        HttpService.decodeSessionHeaders("headerkey-headervalue") should be(left)
      }
    }
  }
}
