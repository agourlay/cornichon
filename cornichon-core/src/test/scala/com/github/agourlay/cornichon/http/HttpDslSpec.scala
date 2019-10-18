package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.core.Session
import utest._

object HttpDslSpec extends TestSuite {

  private val ops = new HttpDslOps {}

  val tests = Tests {
    test("removeFromWithHeaders handles with 'with-headers' not containing the header") {
      val ns = ops.addToWithHeaders("header1", "value1")(Session.newEmpty)
      assert(ops.removeFromWithHeaders("header2")(ns.valueUnsafe) == ns)
    }

    test("removeFromWithHeaders handles with 'with-headers' containing the header and others") {
      val ns1 = ops.addToWithHeaders("header1", "value1")(Session.newEmpty).valueUnsafe
      val ns2 = ops.addToWithHeaders("header2", "value2")(ns1).valueUnsafe
      val ns3 = ops.removeFromWithHeaders("header2")(ns2).valueUnsafe

      assert(HttpService.extractWithHeadersSession(ns1) == HttpService.extractWithHeadersSession(ns3))
    }

    test("removeFromWithHeaders handles with 'with-headers' only the header") {
      val ns1 = ops.addToWithHeaders("header1", "value1")(Session.newEmpty).valueUnsafe
      val ns2 = ops.removeFromWithHeaders("header1")(ns1).valueUnsafe
      assert(HttpService.extractWithHeadersSession(ns2) == Right(Nil))
    }
  }
}
