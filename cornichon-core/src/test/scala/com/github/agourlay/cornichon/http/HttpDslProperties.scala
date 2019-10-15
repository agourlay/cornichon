package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.core.Session
import org.scalacheck.{ Gen, Properties }
import org.scalacheck.Prop._
import org.typelevel.claimant.Claim

class HttpDslProperties extends Properties("HttpDsl") {

  private val ops = new HttpDslOps {}

  property("removeFromWithHeaders handle no 'with-headers'") =
    forAll(Gen.alphaStr) { header ⇒
      Claim {
        ops.removeFromWithHeaders(header)(Session.newEmpty) == Right(Session.newEmpty)
      }
    }
}
