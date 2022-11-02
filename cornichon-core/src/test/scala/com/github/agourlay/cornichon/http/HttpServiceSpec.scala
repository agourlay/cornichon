package com.github.agourlay.cornichon.http

import cats.effect.unsafe.IORuntime
import cats.syntax.show._
import com.github.agourlay.cornichon.core.{ Config, ScenarioContext, Session }
import com.github.agourlay.cornichon.http.HttpMethods.GET
import com.github.agourlay.cornichon.http.client.Http4sClient
import munit.FunSuite
import scala.concurrent.duration._

class HttpServiceSpec extends FunSuite {

  private val dummyRequest = HttpRequest[String](GET, "http://a/b", Some("a test request body"), Nil, Nil)
  implicit val scheduler: IORuntime = cats.effect.unsafe.implicits.global
  private val client = new Http4sClient(true, true, true);
  private val httpService = new HttpService("http://base-url/", 10.seconds, client, new Config())

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

  test("handles URL query parameters") {
    httpService.resolveRequestParts[String]("a/b?p1=v1&p2=v2", None, Nil, Nil, SelectAll)(ScenarioContext.empty) match {
      case Left(e) => assert(cond = false, e)
      case Right((completeUrlResolvedNoParams, jsonBodyResolved, allParams, headersResolved)) =>
        assert(completeUrlResolvedNoParams == "http://base-url/a/b")
        assert(jsonBodyResolved.isEmpty)
        assert(headersResolved == Nil)
        assert(allParams.toSet == Set("p1" -> "v1", "p2" -> "v2"))
    }
  }

  test("handles URL query parameters with additional params") {
    httpService.resolveRequestParts[String]("a/b?p1=v1&p2=v2", None, Seq("p3" -> "v3", "p4" -> "v4"), Nil, SelectAll)(ScenarioContext.empty) match {
      case Left(e) => assert(cond = false, e)
      case Right((completeUrlResolvedNoParams, jsonBodyResolved, allParams, headersResolved)) =>
        assert(completeUrlResolvedNoParams == "http://base-url/a/b")
        assert(jsonBodyResolved.isEmpty)
        assert(headersResolved == Nil)
        assert(allParams.toSet == Set("p1" -> "v1", "p2" -> "v2", "p3" -> "v3", "p4" -> "v4"))
    }
  }
}
