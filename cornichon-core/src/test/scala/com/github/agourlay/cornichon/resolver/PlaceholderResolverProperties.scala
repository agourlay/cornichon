package com.github.agourlay.cornichon.resolver

import java.util.UUID

import com.github.agourlay.cornichon.core.{ KeyNotFoundInSession, RandomContext, Session }
import com.github.agourlay.cornichon.core.SessionProperties._
import org.scalacheck.{ Gen, Properties }
import org.scalacheck.Prop._
import com.github.agourlay.cornichon.resolver.PlaceholderResolver._

class PlaceholderResolverProperties extends Properties("PlaceholderResolver") {

  private val rc = RandomContext.fromSeed(1L)
  private val noExtractor = Map.empty[String, Mapper]

  property("findPlaceholders finds placeholder in content solely containing a placeholder without index") =
    forAll(keyGen) { key =>
      findPlaceholders(s"<$key>") == Right(List(Placeholder(key, None)))
    }

  property("findPlaceholders find placeholder in content solely containing a placeholder with index") =
    forAll(keyGen, indexGen) { (key, index) =>
      findPlaceholders(s"<$key[$index]>") == Right(List(Placeholder(key, Some(index))))
    }

  property("findPlaceholders find placeholder in content starting with whitespace and containing a placeholder") =
    forAll(keyGen) { key =>
      findPlaceholders(s" <$key>") == Right(List(Placeholder(key, None)))
    }

  property("findPlaceholders find placeholder in content starting with 2 whitespaces and containing a placeholder") =
    forAll(keyGen) { key =>
      findPlaceholders(s"  <$key>") == Right(List(Placeholder(key, None)))
    }

  property("findPlaceholders find placeholder in content finishing with whitespace and containing a placeholder") =
    forAll(keyGen) { key =>
      findPlaceholders(s"<$key> ") == Right(List(Placeholder(key, None)))
    }

  property("findPlaceholders find placeholder in content finishing with 2 whitespaces and containing a placeholder") =
    forAll(keyGen) { key =>
      findPlaceholders(s"<$key>  ") == Right(List(Placeholder(key, None)))
    }

  property("findPlaceholders find placeholder in random content containing a placeholder with index") =
    forAll(keyGen, indexGen, Gen.alphaStr) { (key, index, content) =>
      findPlaceholders(s"$content<$key[$index]>$content") == Right(List(Placeholder(key, Some(index))))
    }

  // FIXME '<' is always accepted inside the key, the parser backtracks and consumes it twice??
  //property("findPlaceholders do not accept placeholders containing forbidden char") = {
  //  val genInvalidChar = Gen.oneOf(Session.notAllowedInKey.toList)
  //  forAll(keyGen, indexGen, Gen.alphaStr, genInvalidChar) { (key, index, content, invalid) =>
  //    PlaceholderResolver.findPlaceholders(s"$content<$invalid$key[$index]>$content") == Right(Nil)
  //  }
  //}

  property("fillPlaceholders replaces a single string") =
    forAll(keyGen, valueGen) { (ph, value) =>
      val session = Session.newEmpty.addValueUnsafe(ph, value)
      val content = s"This project is <$ph>"
      fillPlaceholders(content)(session, rc, noExtractor) == Right(s"This project is $value")
    }

  property("fillPlaceholders take the right value in session depending on the index") =
    forAll(keyGen, Gen.nonEmptyListOf(valueGen)) { (key, values) =>
      val input = values.map(v => (key, v))
      val s = Session.newEmpty.addValuesUnsafe(input: _*)
      val (lastKey, lastValue) = input.last
      val content = s"<$lastKey[${input.size - 1}]>"
      fillPlaceholders(content)(s, rc, noExtractor) == Right(lastValue)
    }

  property("fillPlaceholders take the first value in session if index = 0") =
    forAll(keyGen, valueGen, valueGen) { (key, firstValue, secondValue) =>
      val s = Session.newEmpty.addValueUnsafe(key, firstValue).addValueUnsafe(key, secondValue)
      val content = s"<$key[0]>"
      fillPlaceholders(content)(s, rc, noExtractor) == Right(firstValue)
    }

  property("fillPlaceholders returns ResolverError if placeholder not found") =
    forAll(keyGen, valueGen) { (key, value) =>
      val session = Session.newEmpty.addValueUnsafe(key, value)
      val unknownKey = key + "42"
      val content = s"This project is named <$unknownKey>"
      fillPlaceholders(content)(session, rc, noExtractor) == Left(KeyNotFoundInSession(unknownKey, session))
    }

  property("fillPlaceholders resolves two placeholders") =
    forAll(keyGen, valueGen, valueGen) { (k1, v1, v2) =>
      val k2 = k1 + "42"
      val session = Session.newEmpty.addValuesUnsafe(k1 -> v1, k2 -> v2)
      val content = s"This project is named <$k1> and is super <$k2>"
      fillPlaceholders(content)(session, rc, noExtractor) == Right(s"This project is named $v1 and is super $v2")
    }

  property("fillPlaceholders is not be confused by unclosed markup used in a math context") =
    forAll(keyGen, valueGen) { (key, value) =>
      val session = Session.newEmpty.addValueUnsafe(key, value)
      val content = s"3.15 > <$key>"
      PlaceholderResolver.fillPlaceholders(content)(session, rc, noExtractor) == Right(s"3.15 > $value")
    }

  property("fillPlaceholders returns ResolverError for the first placeholder not found") =
    forAll(keyGen, valueGen, keyGen, valueGen) { (k1, v1, k2, v2) =>
      val session = Session.newEmpty.addValuesUnsafe(k1 -> v1, k2 -> v2)
      val content = s"This project is named <$k1> and is super <other-key>"
      fillPlaceholders(content)(session, rc, noExtractor) == Left(KeyNotFoundInSession("other-key", session))
    }

  property("fillPlaceholders generates random uuid if <random-uuid> - fixed by seed") =
    forAll { seed: Long =>
      val session = Session.newEmpty
      val content = "<random-uuid>"
      val first = fillPlaceholders(content)(session, RandomContext.fromSeed(seed), noExtractor).valueUnsafe
      val second = fillPlaceholders(content)(session, RandomContext.fromSeed(seed), noExtractor).valueUnsafe
      UUID.fromString(second) == UUID.fromString(first)
    }

  property("fillPlaceholders generates random positive integer if <random-positive-integer> - fixed by seed") =
    forAll { seed: Long =>
      val session = Session.newEmpty
      val content = "<random-positive-integer>"
      val first = fillPlaceholders(content)(session, RandomContext.fromSeed(seed), noExtractor).valueUnsafe
      val second = fillPlaceholders(content)(session, RandomContext.fromSeed(seed), noExtractor).valueUnsafe
      first.toInt == second.toInt
    }

  property("fillPlaceholders generates random string if <random-string> - fixed by seed") =
    forAll { seed: Long =>
      val session = Session.newEmpty
      val content = "<random-string>"
      val first = fillPlaceholders(content)(session, RandomContext.fromSeed(seed), noExtractor).valueUnsafe
      val second = fillPlaceholders(content)(session, RandomContext.fromSeed(seed), noExtractor).valueUnsafe
      first == second
    }

  property("fillPlaceholders generates random alphanum string if <random-alphanum-string> - fixed by seed") =
    forAll { seed: Long =>
      val session = Session.newEmpty
      val content = "<random-alphanum-string>"
      val first = fillPlaceholders(content)(session, RandomContext.fromSeed(seed), noExtractor).valueUnsafe
      val second = fillPlaceholders(content)(session, RandomContext.fromSeed(seed), noExtractor).valueUnsafe
      first == second
    }

  property("fillPlaceholders generates random boolean if <random-boolean> - fixed by seed") =
    forAll { seed: Long =>
      val session = Session.newEmpty
      val content = "<random-boolean>"
      val first = fillPlaceholders(content)(session, RandomContext.fromSeed(seed), noExtractor).valueUnsafe
      val second = fillPlaceholders(content)(session, RandomContext.fromSeed(seed), noExtractor).valueUnsafe
      first.toBoolean == second.toBoolean
    }

  property("fillPlaceholders generates random timestamp string if <random-timestamp>") =
    forAll { seed: Long =>
      val session = Session.newEmpty
      val content = "<random-timestamp>"
      val first = fillPlaceholders(content)(session, RandomContext.fromSeed(seed), noExtractor).valueUnsafe
      val second = fillPlaceholders(content)(session, RandomContext.fromSeed(seed), noExtractor).valueUnsafe
      new java.util.Date(first.toLong * 1000L) == new java.util.Date(second.toLong * 1000L)
    }

  property("fillPlaceholders generate current timestamp string if <current-timestamp>") =
    forAll { seed: Long =>
      val session = Session.newEmpty
      val content = "<current-timestamp>"
      val first = fillPlaceholders(content)(session, RandomContext.fromSeed(seed), noExtractor).valueUnsafe
      val second = fillPlaceholders(content)(session, RandomContext.fromSeed(seed), noExtractor).valueUnsafe
      new java.util.Date(first.toLong * 1000L).before(new java.util.Date())
      new java.util.Date(second.toLong * 1000L).before(new java.util.Date())
      new java.util.Date(first.toLong * 1000L) == new java.util.Date(second.toLong * 1000L) // valid because delay is less than 1ms
    }

}
