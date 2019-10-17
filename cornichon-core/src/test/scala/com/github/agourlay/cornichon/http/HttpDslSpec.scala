package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.core.Session
import org.scalatest._

class HttpDslSpec extends WordSpec with Matchers with OptionValues with EitherValues {

  private val ops = new HttpDslOps {}

  "HttpDSL" when {

    "removeFromWithHeaders" must {

      "handle with 'with-headers' not containing the header" in {
        val ns = ops.addToWithHeaders("header1", "value1")(Session.newEmpty).valueUnsafe
        ops.removeFromWithHeaders("header2")(ns).right.value should be(ns)
      }

      "handle with 'with-headers' containing the header and others" in {
        val ns1 = ops.addToWithHeaders("header1", "value1")(Session.newEmpty).valueUnsafe
        val ns2 = ops.addToWithHeaders("header2", "value2")(ns1).valueUnsafe
        val ns3 = ops.removeFromWithHeaders("header2")(ns2).valueUnsafe

        HttpService.extractWithHeadersSession(ns1) should be(HttpService.extractWithHeadersSession(ns3))
      }

      "handle with 'with-headers' only the header" in {
        val ns1 = ops.addToWithHeaders("header1", "value1")(Session.newEmpty).valueUnsafe
        val ns2 = ops.removeFromWithHeaders("header1")(ns1).valueUnsafe
        HttpService.extractWithHeadersSession(ns2).right.value should be(Nil)
      }
    }
  }

}
