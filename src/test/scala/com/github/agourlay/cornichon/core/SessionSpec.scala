package com.github.agourlay.cornichon.core

import org.scalatest.{ OptionValues, Matchers, WordSpec }

class SessionSpec extends WordSpec with Matchers with OptionValues {

  "Session" when {
    "get" must {
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
    }
    "removeKey" must {

      "removeKey works" in {
        val s = Session.newSession.addValue("one", "v1")
        s.get("one") should be("v1")
        s.removeKey("one").getOpt("one") should be(None)
      }

      "removeKey does not throw error if key does not exist" in {
        val s = Session.newSession
        s.removeKey("blah")
      }
    }

    "stacked key" must {
      "simple key always takes the last value in session" in {
        val s = Session.newSession.addValue("one", "v1")
        s.addValue("one", "v2").get("one") should be("v2")
      }

      "stacked key with indice zero always takes the first value in session" in {
        val s = Session.newSession.addValue("one", "v1")
        s.addValue("one", "v2").get("one[0]") should be("v1")
      }

      "stacked key with indice one always takes the second value in session" in {
        val s = Session.newSession.addValue("one", "v1")
        s.addValue("one", "v2").get("one[1]") should be("v2")
      }

    }

    "parseIndice" must {
      "parseIndice single digit" in {
        val s = Session.newSession
        s.parseIndice("[1]").value should be("1")
      }

      "parseIndice longer number" in {
        val s = Session.newSession
        s.parseIndice("[12345678]").value should be("12345678")
      }
    }
  }
}
