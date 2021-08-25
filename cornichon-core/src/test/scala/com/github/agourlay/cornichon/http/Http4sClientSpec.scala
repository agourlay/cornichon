package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.http.client.Http4sClient
import monix.execution.Scheduler.Implicits.global
import munit.FunSuite

class Http4sClientSpec extends FunSuite {

  private val client = new Http4sClient(true, true, true)

  override def afterAll(): Unit = {
    client.shutdown().runSyncUnsafe()
    ()
  }

  test("conserves duplicates http params") {
    val uri = client.parseUri("http://web.com").valueUnsafe
    val finalUri = client.addQueryParams(uri, List("p1" -> "v1", "p1" -> "v1'", "p2" -> "v2"))
    assert(finalUri.query.multiParams("p1") == "v1" :: "v1'" :: Nil)
    assert(finalUri.query.multiParams("p2") == "v2" :: Nil)
  }
}
