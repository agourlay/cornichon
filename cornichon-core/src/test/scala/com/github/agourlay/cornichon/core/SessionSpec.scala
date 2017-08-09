package com.github.agourlay.cornichon.core

import cats.scalatest.{ EitherMatchers, EitherValues }
import org.scalatest.prop.PropertyChecks
import org.scalacheck.Gen
import org.scalatest.{ Matchers, WordSpec }
import com.github.agourlay.cornichon.core.SessionSpec._

import scala.util.Random

class SessionSpec extends WordSpec
  with Matchers
  with PropertyChecks
  with EitherValues
  with EitherMatchers {

  "Session" when {
    "addValue" must {
      "throw if key is empty" in {
        intercept[CornichonException] {
          Session.newEmpty.addValue("", Random.nextString(5))
        }
      }

      "throw if key contains illegal chars" in {
        forAll(keyGen, Gen.oneOf(Session.notAllowedInKey.trim)) { (key, forbiddenChar) ⇒
          intercept[CornichonException] {
            Session.newEmpty.addValue(key + forbiddenChar, Random.nextString(5))
          }
        }
      }
    }

    "get" must {
      "return a written value" in {
        forAll(keyGen, valueGen) { (key, value) ⇒
          val s2 = Session.newEmpty.addValue(key, value)
          s2.get(key) should beRight(value)
        }
      }

      "throw an error if the key does not exist" in {
        forAll(keyGen) { key ⇒
          val s = Session.newEmpty
          s.get(key) should beLeft[CornichonError](KeyNotFoundInSession(key, None, s))
        }
      }

      "take the last value in session without index param" in {
        forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
          val s = Session.newEmpty.addValue(key, firstValue)
          s.addValue(key, secondValue).get(key) should beRight(secondValue)
        }
      }

      "take the first value in session with indice = zero" in {
        forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
          val s = Session.newEmpty.addValue(key, firstValue)
          s.addValue(key, secondValue).get(key, Some(0)) should beRight(firstValue)
        }
      }

      "take the second value in session with indice = 1" in {
        forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
          val s = Session.newEmpty.addValue(key, firstValue)
          s.addValue(key, secondValue).get(key, Some(1)) should beRight(secondValue)
        }
      }

      "thrown an error if the indice is negative" in {
        forAll(keyGen, valueGen) { (key, firstValue) ⇒
          val s = Session.newEmpty.addValue(key, firstValue)
          s.get(key, Some(-1)) should beLeft[CornichonError](KeyNotFoundInSession(key, Some(-1), s))
        }
      }
    }

    "getList" must {
      "throw an error if one of the key does not exist" in {
        forAll(keyGen, keyGen, keyGen, valueGen, valueGen) { (firstKey, secondKey, thirdKey, firstValue, secondValue) ⇒
          val s2 = Session
            .newEmpty
            .addValue(firstKey, firstValue)
            .addValue(secondKey, secondValue)
          s2.getList(Seq(firstKey, thirdKey)) should be(left)
        }
      }
    }

    "getOps" must {
      "return None if key does not exist" in {
        forAll(keyGen) { key ⇒
          Session.newEmpty.getOpt(key) should be(None)
        }
      }
    }

    "removeKey" must {
      "remove entry" in {
        forAll(keyGen, valueGen) { (key, value) ⇒
          val s = Session.newEmpty.addValue(key, value)
          s.get(key) should beRight(value)
          s.removeKey(key).getOpt(key) should be(None)
        }
      }

      "not throw error if key does not exist" in {
        forAll(keyGen) { key ⇒
          Session.newEmpty.removeKey(key)
        }
      }
    }
  }
}

object SessionSpec {
  val keyGen = Gen.alphaStr
    .filter(_.trim.nonEmpty)
    .filter(k ⇒ !Session.notAllowedInKey.exists(forbidden ⇒ k.contains(forbidden)))
  def keysGen(n: Int) = Gen.listOfN(n, keyGen)

  val valueGen = Gen.alphaStr
  def valuesGen(n: Int) = Gen.listOfN(n, valueGen)

  val indiceGen = Gen.choose(0, Int.MaxValue)
}
