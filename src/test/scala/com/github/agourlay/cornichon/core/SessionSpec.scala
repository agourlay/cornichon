package com.github.agourlay.cornichon.core

import org.scalatest.{ Matchers, WordSpec }

class SessionSpec extends WordSpec with Matchers {

  "Session" must {
    "get throws error if key does not exist" in {
      val s = Session.newSession
      intercept[KeyNotFoundInSession] {
        s.get("blah")
      }
    }

    "getList throws error if one of the key does not exist" in {
      val s = Session.newSession
      s.addValue("one", "v1").addValue("two", "v2")
      intercept[KeyNotFoundInSession] {
        s.getList(Seq("one", "three"))
      }
    }

    "getOpt return None if key does not exist" in {
      val s = Session.newSession
      s.getOpt("blah") should be(None)
    }

    "removeKey works" in {
      val s = Session.newSession.addValue("one", "v1")
      s.get("one") should be("v1")
      s.removeKey("one").getOpt("one") should be(None)
    }

    "removeKey does not throw error if key does not exist" in {
      val s = Session.newSession
      s.removeKey("blah")
    }

    "addValue replace existing value" in {
      val s = Session.newSession.addValue("one", "v1")
      s.get("one") should be("v1")
      s.addValue("one", "v2").get("one") should be("v2")
    }
  }
}
