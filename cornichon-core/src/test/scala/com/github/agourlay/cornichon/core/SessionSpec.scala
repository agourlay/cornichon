package com.github.agourlay.cornichon.core

import org.scalacheck.Gen
import org.scalatest.{ Matchers, OptionValues, WordSpec }
import cats.syntax.either._

class SessionSpec extends WordSpec
  with Matchers
  with OptionValues {

  "Session" when {

    "get" must {

      "propose similar keys in Session in case of a missing key" in {
        val s = Session.newEmpty.addValuesUnsafe("my-key" -> "blah", "my_keys" -> "bloh", "not-my-key" -> "blih")
        s.get("my_key").leftMap(_.renderedMessage) should be(Left("key 'my_key' can not be found in session maybe you meant 'my-key' or 'my_keys'\nmy-key -> Values(blah)\nmy_keys -> Values(bloh)\nnot-my-key -> Values(blih)"))
      }
    }

  }
}

object SessionSpec {
  val keyGen: Gen[String] = Gen.alphaStr
    .filter(_.trim.nonEmpty)
    .filter(k ⇒ !Session.notAllowedInKey.exists(forbidden ⇒ k.contains(forbidden)))

  val badKeyGen: Gen[String] = Gen.nonEmptyListOf(Session.notAllowedInKey).filter(_.nonEmpty).map(_.toString())

  val valueGen = Gen.alphaStr
  def valuesGen(n: Int) = Gen.listOfN(n, valueGen)

  val indexGen = Gen.choose(0, Int.MaxValue)
}
