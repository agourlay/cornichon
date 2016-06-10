package com.github.agourlay.cornichon.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import cats.data.Xor.right
import com.github.agourlay.cornichon.core.{ Resolver, Session }
import com.github.agourlay.cornichon.http.client.AkkaHttpClient
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class HttpServiceSpec extends WordSpec with Matchers with BeforeAndAfterAll {

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
        val s = Session.newSession
        val resp = CornichonHttpResponse(StatusCodes.OK, Nil, "hello world")
        val filledSession = service.fillInSessionWithResponse(s, resp, NoOpExtraction)
        filledSession.get("last-response-status") should be("200")
        filledSession.get("last-response-body") should be("hello world")
      }

      "extract content with RootResponseExtraction" in {
        val s = Session.newSession
        val resp = CornichonHttpResponse(StatusCodes.OK, Nil, "hello world")
        val filledSession = service.fillInSessionWithResponse(s, resp, RootExtractor("copy-body"))
        filledSession.get("last-response-status") should be("200")
        filledSession.get("last-response-body") should be("hello world")
        filledSession.get("copy-body") should be("hello world")
      }

      "extract content with PathResponseExtraction" in {
        val s = Session.newSession
        val resp = CornichonHttpResponse(StatusCodes.OK, Nil,
          """
            {
              "name" : "batman"
            }
          """)
        val filledSession = service.fillInSessionWithResponse(s, resp, PathExtractor("name", "part-of-body"))
        filledSession.get("last-response-status") should be("200")
        filledSession.get("last-response-body") should be(
          """
            {
              "name" : "batman"
            }
          """
        )
        filledSession.get("part-of-body") should be("batman")
      }
    }

    "resolveParams" must {
      "resolve also params in URL" in {
        val s = Session.newSession
          .addValues(Seq("hero" → "batman", "color" → "blue"))
        val url = "http://yada.com?hero=<hero>&color=<color>"
        service.resolveParams(url, params = Seq.empty)(s) should be(right(Seq("hero" → "batman", "color" → "blue")))
      }

      "detect non resolvable params" in {
        val s = Session.newSession
          .addValues(Seq("hero" → "batman", "color" → "blue"))
        val url = "http://yada.com?hero=<hero>&color=<color2>"
        service.resolveParams(url, params = Seq.empty)(s).isLeft should be(true)
      }

      "handle URL without param" in {
        val s = Session.newSession
        val url = "http://yada.com"
        service.resolveParams(url, params = Seq.empty)(s) should be(right(Seq.empty))
      }
    }

  }
}
