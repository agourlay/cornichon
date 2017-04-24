package com.github.agourlay.cornichon.core

import cats.scalatest.{ EitherMatchers, EitherValues }
import org.scalatest.prop.PropertyChecks
import org.scalacheck.Gen
import org.scalatest.{ Matchers, WordSpec }
import com.github.agourlay.cornichon.core.SessionSpec._

class SessionSpec extends WordSpec
    with Matchers
    with PropertyChecks
    with EitherValues
    with EitherMatchers {

  "Session" when {
    "addValue" must {
      "throw if key is empty" in {
        val s = Session.newEmpty
        forAll(valueGen) { value ⇒
          intercept[CornichonException] {
            s.addValue("", value)
          }
        }
      }
    }

    "get" must {
      "return a written value" in {
        forAll(keyGen, valueGen) { (key, value) ⇒
          val s1 = Session.newEmpty
          val s2 = s1.addValue(key, value)
          s2.getUnsafe(key) should be(value)
        }
      }

      "throw an error if the key does not exist" in {
        forAll(keyGen) { key ⇒
          val s = Session.newEmpty
          intercept[CornichonException] {
            s.getUnsafe(key)
          }
        }
      }

      "take the last value in session without index param" in {
        forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
          val s = Session.newEmpty.addValue(key, firstValue)
          s.addValue(key, secondValue).getUnsafe(key) should be(secondValue)
        }
      }

      "take the first value in session with indice = zero" in {
        forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
          val s = Session.newEmpty.addValue(key, firstValue)
          s.addValue(key, secondValue).getUnsafe(key, Some(0)) should be(firstValue)
        }
      }

      "take the second value in session with indice = 1" in {
        forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
          val s = Session.newEmpty.addValue(key, firstValue)
          s.addValue(key, secondValue).getUnsafe(key, Some(1)) should be(secondValue)
        }
      }

      "thrown an error if the indice is negative" in {
        forAll(keyGen, valueGen) { (key, firstValue) ⇒
          val s = Session.newEmpty.addValue(key, firstValue)
          intercept[CornichonException] {
            s.getUnsafe(key, Some(-1))
          }
        }
      }
    }

    "getList" must {
      "throw an error if one of the key does not exist" in {
        forAll(keyGen, keyGen, keyGen, valueGen, valueGen) { (firstKey, secondKey, thirdKey, firstValue, secondValue) ⇒
          val s = Session.newEmpty
          s.addValue(firstKey, firstValue).addValue(secondKey, secondValue)
          s.getList(Seq(firstKey, thirdKey)) should be(left)
        }
      }
    }

    "getOps" must {
      "return None if key does not exist" in {
        forAll(keyGen) { key ⇒
          val s = Session.newEmpty
          s.getOpt(key) should be(None)
        }
      }
    }

    "removeKey" must {
      "remove entry" in {
        forAll(keyGen, valueGen) { (key, value) ⇒
          val s = Session.newEmpty.addValue(key, value)
          s.getUnsafe(key) should be(value)
          s.removeKey(key).getOpt(key) should be(None)
        }
      }

      "not throw error if key does not exist" in {
        forAll(keyGen) { key ⇒
          val s = Session.newEmpty
          s.removeKey(key)
        }
      }
    }
  }
}

object SessionSpec {
  val keyGen = Gen.alphaStr.filter(_.nonEmpty)
  def keysGen(n: Int) = Gen.listOfN(n, keyGen)

  val valueGen = Gen.alphaStr
  def valuesGen(n: Int) = Gen.listOfN(n, valueGen)

  val indiceGen = Gen.choose(0, Int.MaxValue)
}
