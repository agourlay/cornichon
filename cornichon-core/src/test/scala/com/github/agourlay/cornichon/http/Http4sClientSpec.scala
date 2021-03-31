package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.http.client.SttpClient
import com.github.agourlay.cornichon.http.client.SttpClient.Https4BackendOption
import monix.execution.Scheduler.Implicits.global
import utest._

object Http4sClientSpec extends TestSuite {

  private val client = SttpClient(Https4BackendOption)(addAcceptGzipByDefault = true, disableCertificateVerification = true, followRedirect = true)

  override def utestAfterAll(): Unit = {
    client.shutdown.runSyncUnsafe()
  }

  def tests = Tests {
    test("conserves duplicates http params") {
      val uri = client.parseUri("http://web.com").valueUnsafe
      val finalUri = uri.addParams("p1" -> "v1", "p1" -> "v1'", "p2" -> "v2")

      assert(finalUri.params.getMulti("p1").contains(List("v1", "v1'")))
      assert(finalUri.params.getMulti("p2").contains(List("v2")))
    }
  }
}
