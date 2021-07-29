package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.http.client.Http4sClient
import monix.execution.Scheduler.Implicits.global
import utest._

object Http4sClientSpec extends TestSuite {

  private val client = new Http4sClient(true, true, true)

  override def utestAfterAll(): Unit = {
    client.shutdown().runSyncUnsafe()
    ()
  }

  def tests = Tests {
    test("conserves duplicates http params") {
      val uri = client.parseUri("http://web.com").valueUnsafe
      val finalUri = client.addQueryParams(uri, List("p1" -> "v1", "p1" -> "v1'", "p2" -> "v2"))
      assert(finalUri.query.multiParams("p1") == "v1" :: "v1'" :: Nil)
      assert(finalUri.query.multiParams("p2") == "v2" :: Nil)
    }
  }
}
