package com.github.agourlay.cornichon.http

import cats.effect.unsafe.IORuntime
import com.github.agourlay.cornichon.core.{Config, ScenarioContext, Session}
import com.github.agourlay.cornichon.http.HttpMethods.GET
import com.github.agourlay.cornichon.http.client.Http4sClient
import munit.FunSuite

import scala.collection.immutable.ArraySeq
import scala.concurrent.duration._

class HttpServiceSpec extends FunSuite {

  private val dummyRequest = HttpRequest[String](GET, "http://a/b", Some("a test request body"), Nil, Nil)
  implicit val scheduler: IORuntime = cats.effect.unsafe.implicits.global
  private val client = new Http4sClient(true, true, true, false)
  private val httpService = new HttpService("http://base-url/", 10.seconds, client, new Config())

  test("fillInSessionWithResponse extracts content with NoOpExtraction") {
    val resp = HttpResponse(200, ArraySeq.empty, "hello world")
    val filledSession = HttpService.fillInSessionWithResponse(resp, Session.newEmpty, NoOpExtraction, dummyRequest.detailedDescription)
    assert(filledSession.flatMap(_.get("last-response-status")) == Right("200"))
    assert(filledSession.flatMap(_.get("last-response-body")) == Right("hello world"))
    assert(filledSession.flatMap(_.get("last-response-request")) == Right(dummyRequest.detailedDescription))
  }

  test("fillInSessionWithResponse extracts content with RootResponseExtraction") {
    val resp = HttpResponse(200, ArraySeq.empty, "hello world")
    val filledSession = HttpService.fillInSessionWithResponse(resp, Session.newEmpty, RootExtractor("copy-body"), dummyRequest.detailedDescription)
    assert(filledSession.flatMap(_.get("last-response-status")) == Right("200"))
    assert(filledSession.flatMap(_.get("last-response-body")) == Right("hello world"))
    assert(filledSession.flatMap(_.get("copy-body")) == Right("hello world"))
  }

  test("fillInSessionWithResponse extracts content with PathResponseExtraction") {
    val resp = HttpResponse(200, ArraySeq.empty, """{ "name" : "batman" }""")
    val filledSession = HttpService.fillInSessionWithResponse(resp, Session.newEmpty, PathExtractor("name", "part-of-body"), dummyRequest.detailedDescription)
    assert(filledSession.flatMap(_.get("last-response-status")) == Right("200"))
    assert(filledSession.flatMap(_.get("last-response-body")) == Right("""{ "name" : "batman" }"""))
    assert(filledSession.flatMap(_.get("part-of-body")) == Right("batman"))
  }

  test("decodeSessionHeaders fail a if wrong format") {
    assert(HttpService.decodeSessionHeaders("headerkey-headervalue").isLeft)
  }

  test("handles URL query parameters") {
    httpService.resolveRequestParts[String]("a/b?p1=v1&p2=v2", None, Nil, Nil, SelectAll)(ScenarioContext.empty) match {
      case Left(e)                                                                            => assert(cond = false, e)
      case Right((completeUrlResolvedNoParams, jsonBodyResolved, allParams, headersResolved)) =>
        assert(completeUrlResolvedNoParams == "http://base-url/a/b")
        assert(jsonBodyResolved.isEmpty)
        assert(headersResolved == Nil)
        assert(allParams.toSet == Set("p1" -> "v1", "p2" -> "v2"))
    }
  }

  test("handles URL query parameters with additional params") {
    httpService.resolveRequestParts[String]("a/b?p1=v1&p2=v2", None, Seq("p3" -> "v3", "p4" -> "v4"), Nil, SelectAll)(ScenarioContext.empty) match {
      case Left(e)                                                                            => assert(cond = false, e)
      case Right((completeUrlResolvedNoParams, jsonBodyResolved, allParams, headersResolved)) =>
        assert(completeUrlResolvedNoParams == "http://base-url/a/b")
        assert(jsonBodyResolved.isEmpty)
        assert(headersResolved == Nil)
        assert(allParams.toSet == Set("p1" -> "v1", "p2" -> "v2", "p3" -> "v3", "p4" -> "v4"))
    }
  }

  test("encode and decode headers roundtrip") {
    val headers = ArraySeq("Content-Type" -> "application/json", "Authorization" -> "Bearer token123")
    val encoded = HttpService.encodeSessionHeaders(headers)
    val decoded = HttpService.decodeSessionHeaders(encoded)
    assert(decoded == Right(headers.toList))
  }

  test("header value containing the key-value delimiter roundtrips correctly") {
    val headers = ArraySeq("X-Custom" -> "value→with→arrows")
    val encoded = HttpService.encodeSessionHeaders(headers)
    val decoded = HttpService.decodeSessionHeaders(encoded)
    assert(decoded == Right(headers.toList))
  }

  // Known bug: inter-header delimiter '¦' in values breaks decoding
  test("header value containing the inter-header delimiter".ignore) {
    val headers = ArraySeq("X-Custom" -> "value¦with¦pipes")
    val encoded = HttpService.encodeSessionHeaders(headers)
    val decoded = HttpService.decodeSessionHeaders(encoded)
    assert(decoded == Right(headers.toList))
  }

}
