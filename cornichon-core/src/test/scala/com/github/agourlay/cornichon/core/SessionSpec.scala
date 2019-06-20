package com.github.agourlay.cornichon.core

import cats.scalatest.{ EitherMatchers, EitherValues }
import org.scalacheck.Gen
import org.scalatest.{ Matchers, OptionValues, WordSpec }
import com.github.agourlay.cornichon.core.SessionSpec._
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.util.Random

class SessionSpec extends WordSpec
  with Matchers
  with ScalaCheckPropertyChecks
  with OptionValues
  with EitherValues
  with EitherMatchers {

  "Session" when {
    "addValue" must {
      "throw if key is empty" in {
        intercept[CornichonException] {
          Session.newEmpty.addValueUnsafe("", Random.nextString(5))
        }
      }

      "throw if key contains illegal chars" in {
        forAll(keyGen, Gen.oneOf(Session.notAllowedInKey.trim)) { (key, forbiddenChar) ⇒
          intercept[CornichonException] {
            Session.newEmpty.addValueUnsafe(key + forbiddenChar, Random.nextString(5))
          }
        }
      }

      "write the value" in {
        forAll(keyGen, valueGen) { (key, value) ⇒
          val s2 = Session.newEmpty.addValueUnsafe(key, value)
          s2.content.get(key).value should be(Vector(value))
        }
      }
    }

    "addValues" must {
      "throw if one key is empty" in {
        intercept[CornichonException] {
          Session.newEmpty.addValuesUnsafe("a" -> Random.nextString(5), "" -> Random.nextString(5))
        }
      }

      "throw if key contains illegal chars" in {
        forAll(keyGen, Gen.oneOf(Session.notAllowedInKey.trim)) { (key, forbiddenChar) ⇒
          intercept[CornichonException] {
            Session.newEmpty.addValuesUnsafe(key -> Random.nextString(5), key + forbiddenChar -> Random.nextString(5))
          }
        }
      }

      "write the values" in {
        forAll(keyGen, valueGen, keyGen, valueGen) { (k1, v1, k2, v2) ⇒
          val s2 = Session.newEmpty.addValuesUnsafe(k1 -> v1, k2 -> v2)
          if (k1 == k2)
            s2.content.get(k1).value should be(Vector(v1, v2))
          else {
            s2.content.get(k1).value should be(Vector(v1))
            s2.content.get(k2).value should be(Vector(v2))
          }
        }
      }

    }

    "get" must {
      "return a written value" in {
        forAll(keyGen, valueGen) { (key, value) ⇒
          val s2 = Session.newEmpty.addValueUnsafe(key, value)
          s2.get(key) should beRight(value)
        }
      }

      "throw an error if the key does not exist" in {
        forAll(keyGen) { key ⇒
          val s = Session.newEmpty
          s.get(key) should beLeft[CornichonError](KeyNotFoundInSession(key, s))
        }
      }

      "take the last value in session without index param" in {
        forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
          val s = Session.newEmpty.addValueUnsafe(key, firstValue)
          s.addValueUnsafe(key, secondValue).get(key) should beRight(secondValue)
        }
      }

      "take the first value in session with indice = zero" in {
        forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
          val s = Session.newEmpty.addValueUnsafe(key, firstValue)
          s.addValueUnsafe(key, secondValue).get(key, Some(0)) should beRight(firstValue)
        }
      }

      "take the second value in session with indice = 1" in {
        forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
          val s = Session.newEmpty.addValueUnsafe(key, firstValue)
          s.addValueUnsafe(key, secondValue).get(key, Some(1)) should beRight(secondValue)
        }
      }

      "thrown an error if the key exists but not the indice " in {
        forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
          val s = Session.newEmpty.addValueUnsafe(key, firstValue).addValueUnsafe(key, secondValue)
          val error = IndiceNotFoundForKey(key, 3, Vector(firstValue, secondValue))
          s.get(key, Some(3)) should beLeft[CornichonError](error)
          error.renderedMessage should be(s"indice '3' not found for key '$key' with values \n0 -> $firstValue\n1 -> $secondValue")
        }
      }

      "propose similar keys in Session in case of a missing key" in {
        val s = Session.newEmpty.addValuesUnsafe("my-key" -> "blah", "my_keys" -> "bloh", "not-my-key" -> "blih")
        s.get("my_key").leftValue.renderedMessage should be("key 'my_key' can not be found in session maybe you meant 'my-key' or 'my_keys'\nmy-key -> Values(blah)\nmy_keys -> Values(bloh)\nnot-my-key -> Values(blih)")
      }
    }

    "getPrevious" must {
      "return None if the key has only one value" in {
        forAll(keyGen, valueGen) { (key, firstValue) ⇒
          val s = Session.newEmpty.addValueUnsafe(key, firstValue)
          s.getPrevious(key).value should be(None)
        }
      }

      "return an Option of the previous value in session" in {
        forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) ⇒
          val s = Session.newEmpty.addValueUnsafe(key, firstValue).addValueUnsafe(key, secondValue)
          s.getPrevious(key).value should be(Some(firstValue))
        }
      }
    }

    "getList" must {
      "throw an error if one of the key does not exist" in {
        forAll(keyGen, keyGen, keyGen, valueGen, valueGen) { (firstKey, secondKey, thirdKey, firstValue, secondValue) ⇒
          val s2 = Session
            .newEmpty
            .addValueUnsafe(firstKey, firstValue)
            .addValueUnsafe(secondKey, secondValue)
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
          val s = Session.newEmpty.addValueUnsafe(key, value)
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

    "rollbackKey" must {
      "rollback properly" in {
        forAll(keyGen, valueGen, valueGen) { (key, value1, value2) ⇒
          val s = Session.newEmpty.addValueUnsafe(key, value1).addValueUnsafe(key, value2)
          s.get(key) should beRight(value2)
          s.rollbackKey(key).value.get(key) should beRight(value1)
        }
      }

      "delete key if it has only one value" in {
        forAll(keyGen, valueGen) { (key, value) ⇒
          val s = Session.newEmpty.addValueUnsafe(key, value)
          s.get(key) should beRight(value)
          s.rollbackKey(key).value.getOpt(key) should be(None)
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
